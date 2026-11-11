# /// script
# requires-python = ">=3.10"
# dependencies = ["requests>=2.32", "cryptography>=43"]
# ///
"""海信爱家接口工具。status/functions/plan 只读；on/off 会实际控制新风。

Protocol evidence: local APK 7.0.9.3, its model-specific panel 3.1.14.7,
and authenticated read/refresh requests on 2026-11-11.
Account protocol cross-reference:
https://github.com/manymuch/HisenseHA/blob/main/custom_components/hisense/pyhisenseapi.py
https://github.com/sfairy/ha_hisense/blob/main/custom_components/hisense/api.py
The on/off path is derived from app code and has NOT been executed on the AC.
"""
import argparse
import base64
import fcntl
import getpass
import hashlib
import json
import os
from pathlib import Path
import sys
import time
import uuid

import requests

BASE = "https://iot-aihome.hismarttv.com"
APP_KEY = "commonweb"
# Published web-client key, not a user's account credential.
APP_SECRET = "MORZRbkuiWxjp+SM4vR_GxY4pZxLZ6rn"
MODEL = "KFR-35G/X520U-X1(1X18)"
PREFIX = "connector.device.command."


def encode(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode()


def headers(raw):
    digest = hashlib.md5(raw + APP_SECRET.encode()).digest()
    return {"Content-Type": "application/json; charset=UTF-8",
            "appKey": APP_KEY,
            "X-Sign-For": base64.b64encode(digest).decode()}


def save_private(path, value):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = path.with_suffix(".tmp")
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as stream:
        stream.write(json.dumps(value, ensure_ascii=False, indent=2))
    os.chmod(temporary, 0o600)
    os.replace(temporary, path)


class API:
    def __init__(self, storage):
        self.storage = storage
        self.http = requests.Session()
        self.auth = json.loads(storage.read_text()) if storage.exists() else {}

    def accept_auth(self, data):
        if not data.get("token") or not data.get("customerId"):
            raise RuntimeError("认证服务未返回完整令牌；本地会话未更新。")
        keys = ("token", "customerId", "refreshToken", "subscriberId",
                "tokenExpiredTime", "refreshTokenExpiredTime", "tokenCreateTime")
        updated = dict(self.auth)
        updated.update({key: data[key] for key in keys if key in data})
        updated["obtainedAt"] = time.time()
        save_private(self.storage, updated)
        self.auth = updated

    def refresh(self):
        token = self.auth.get("refreshToken")
        if not token:
            raise RuntimeError("没有刷新令牌，请先运行 login。")
        response = self.http.post(
            "https://bas-wg.hismarttv.com/aaa/refresh_token2",
            data={"refreshToken": token, "appKey": "2", "format": "1"}, timeout=20)
        response.raise_for_status()
        result = response.json()
        data = result[0] if isinstance(result, list) and result else result.get("data", {})
        if str(data.get("resultCode", "0")) != "0" or not data.get("token"):
            raise RuntimeError("令牌续期失败，请在本机重新登录。")
        self.accept_auth(data)

    def login(self):
        from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

        def encrypted(text):
            raw = text.encode()
            count = 16 - len(raw) % 16
            cipher = Cipher(algorithms.AES(APP_SECRET.encode()),
                            modes.CBC(APP_SECRET[:16].encode())).encryptor()
            output = cipher.update(raw + bytes([count]) * count) + cipher.finalize()
            return base64.b64encode(output).decode()

        username = input("海信账号：").strip()
        password = getpass.getpass("密码（仅在本机使用，不保存）：")
        raw = encode({"loginName": encrypted(username), "signature": encrypted(password),
                      "serverCode": "9501", "distributeId": "2001", "termType": 2})
        response = self.http.post(
            "https://portal-account.hismarttv.com/mobile/se/signon",
            params={"lastUpdateTime": "0", "version": "1.0", "deviceType": "2",
                    "appType": "100", "versionCode": "101", "adaptertRank": "720",
                    "_": str(int(time.time() * 1000))},
            headers=headers(raw), data=raw, timeout=20)
        response.raise_for_status()
        data = response.json().get("data", {})
        if str(data.get("resultCode")) != "0":
            raise RuntimeError("登录失败；该密码登录分支尚未使用你的账号验证。")
        token_info = data.get("tokenInfo", {})
        if not token_info.get("customerId") and data.get("customerId"):
            token_info["customerId"] = data["customerId"]
        self.accept_auth(token_info)

    def envelope(self, payload, execute=False):
        body = {"appVersion": "7.0.9.3", "appType": 1,
                "header": {"messageId": str(uuid.uuid4()),
                           "timestamp": int(time.time() * 1000), "languageId": 0},
                "accessToken": self.auth.get("token", ""), "payload": payload}
        if execute:
            body["sourceName"] = "smartlifeAppH5"
        return body

    def post(self, path, payload, execute=False):
        if not self.auth.get("token"):
            raise RuntimeError("没有本地会话，请先运行 login。")
        # Refresh before a three-day access token approaches expiration.
        ttl = float(self.auth.get("tokenExpiredTime", 259200))
        if time.time() - self.auth.get("obtainedAt", 0) > max(0, ttl - 3600):
            self.refresh()
        for attempt in range(2):
            raw = encode(self.envelope(payload, execute))
            response = self.http.post(BASE + path, headers=headers(raw), data=raw, timeout=20)
            result = response.json()
            state = result.get("payload", {})
            if response.ok and state.get("status") == "SUCCESS":
                return state
            reason = " ".join(str(node.get(key, "")) for node in (result, state)
                              for key in ("errorDesc", "message", "errorCode"))
            auth_error = (response.status_code in (401, 403)
                          or result.get("tokenErrorCode") is not None
                          or "token" in reason.lower())
            if attempt == 0 and auth_error:
                self.refresh()
                continue
            # Never dump raw authentication responses into terminal output.
            raise RuntimeError(f"接口请求失败：HTTP {response.status_code}，"
                               f"status={state.get('status', 'unknown')}。")
        raise RuntimeError("认证重试失败。")

    def target(self, device_id=None):
        data = self.post("/4.0/iot/devices/list", {
            "sourceType": 20, "customerId": self.auth["customerId"],
            "withState": True, "withMultiSplit": True, "whitelist": 1})
        devices = [device for device in data.get("devices", [])
                   if (device.get("deviceId") == device_id if device_id else
                       device.get("product", {}).get("name") == MODEL)]
        if len(devices) != 1:
            raise RuntimeError("目标设备不唯一或未找到，请使用 --device-id 指定设备。")
        return devices[0]

    def execute_payload(self, device, enabled):
        actions = [{"command": PREFIX + "SetVentilationWindSwitch",
                    "params": {"ventilationWindSwitch": "1" if enabled else "0"}}]
        if enabled:
            actions.append({"command": PREFIX + "SetVentilationModes",
                            "params": {"ventilationModes": "1"}})
        return {"sourceType": "20", "exclusionFlag": 1,
                "customerId": str(self.auth["customerId"]),
                "deviceId": device["deviceId"], "partnerId": "1001", "actions": actions}

    def change(self, device, enabled):
        self.post("/4.0/iot/devices/execute", self.execute_payload(device, enabled), True)
        self.post("/4.0/iot/state/refreshDeviceState", {
            "deviceIds": [{"partnerId": "1001", "deviceId": device["deviceId"]}]})
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            time.sleep(2)
            current = self.target(device["deviceId"])["states"]
            if current.get("ventilationWindSwitch") == ("1" if enabled else "0"):
                if not enabled or current.get("ventilationModes") == "1":
                    print("已回读确认：新风外循环开启。" if enabled else "已回读确认：新风关闭。")
                    return
        raise RuntimeError("指令已提交，但20秒内未确认目标状态；请检查设备状态。")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["status", "functions", "plan", "login", "refresh", "on", "off"])
    parser.add_argument("--device-id")
    parser.add_argument("--session", type=Path,
                        default=Path(__file__).resolve().parent / ".local/session.json")
    args = parser.parse_args()
    args.session.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with (args.session.parent / "session.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        api = API(args.session)
        if args.command in ("login", "refresh"):
            getattr(api, args.command)()
            print("本地会话已更新。")
            return
        device = api.target(args.device_id)
        if args.command == "status":
            states = device["states"]
            print(json.dumps({"model": device["product"]["name"],
                              **{key: states.get(key) for key in
                                 ("onlineStatus", "powerStatus", "ventilationWindSwitch",
                                  "ventilationModes", "autoVentilationWind", "ventilationWindSpeed")}},
                             ensure_ascii=False, indent=2))
        elif args.command == "functions":
            result = api.post("/4.0/iot/devices/function/getByProduct", {
                "sourceType": 20, "products": [{"productCode": device["product"]["code"],
                "categoryCode": device["categoryCode"], "partnerId": 1001,
                "deviceId": device["deviceId"]}]})
            selected = [item for product in result["products"] for item in product["functions"]
                        if "Ventilation" in item["code"]]
            print(json.dumps(selected, ensure_ascii=False, indent=2))
        elif args.command == "plan":
            for label, enabled in [("17:00 Asia/Shanghai", True), ("20:00 Asia/Shanghai", False)]:
                payload = api.execute_payload(device, enabled)
                payload["customerId"] = "<customerId>"
                payload["deviceId"] = "<deviceId>"
                print(json.dumps({"time": label, "method": "POST",
                                  "url": BASE + "/4.0/iot/devices/execute", "payload": payload},
                                 ensure_ascii=False, indent=2))
        else:
            api.change(device, args.command == "on")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, requests.RequestException, ValueError, OSError) as error:
        if isinstance(error, requests.RequestException):
            print("网络请求失败，请检查连接后重试。", file=sys.stderr)
        else:
            print(str(error), file=sys.stderr)
        sys.exit(1)
