<div align="center">

**[English](README.md)｜中文**

# 🗄️ Cupboard

一款專為 Minecraft PvP 伺服器設計的 Rust 風格領地保護插件。

![Platforms](https://img.shields.io/badge/platforms-Spigot%20%7C%20Paper%20%7C%20Purpur%20%7C%20Folia%20%7C%20Leaves%20%7C%20Luminol-blue)
![Minecraft](https://img.shields.io/badge/minecraft-1.20%2B-brightgreen)
![Java](https://img.shields.io/badge/java-17%2B-orange)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

</div>

Cupboard 讓玩家只要放置一塊**金磚**，就能保護自己的建築與物品，同時保留像 TNT 破壞這類具攻擊性的玩法——一切都能依照你伺服器的平衡與風格自由設定。

---

## 目錄

- [簡介](#簡介)
- [特色功能](#特色功能)
- [保護內容說明](#保護內容說明)
- [指令與權限](#指令與權限)
- [安裝方式](#安裝方式)
- [語言檔](#語言檔)
- [相容性](#相容性)
- [授權條款](#授權條款)
- [致謝](#致謝)

---

## 簡介

Cupboard 加入了類似 Rust 的領地保護系統。玩家只要放置一塊金磚，就能以該金磚為中心宣告一個 **19×19×19** 的立方體保護區域。在此範圍內，未經授權的玩家無法放置或破壞方塊——而 TNT 等爆炸物依然有效（可設定），讓攻城掠地的玩法得以保留，同時不破壞 Minecraft 原有的核心機制。

## 特色功能

| 分類 | 內容 |
|---|---|
| 🛡️ 保護機制 | 以金磚為中心的 19×19×19 保護區，右鍵點擊金磚即可授權 |
| 💥 TNT | 可設定保護區內是否受 TNT 傷害；TNT 可破壞黑曜石、水與岩漿 |
| 🧟 苦力怕 | 預設阻擋苦力怕爆炸傷害（可設定） |
| 🌀 地獄傳送門 | 防止被固體方塊擋住、被岩漿覆蓋，或被非玩家實體使用；搜尋半徑可自訂 |
| 🐷 其他 | 殭屍豬人會掉落地獄疣 |
| 🌍 世界邊界 | 可設定邊界、隨時間縮小、調整地獄與終界的縮放比例 |
| 🌐 多語言 | 完整支援語言檔翻譯 |

## 保護內容說明

**✅ 受到保護**
1. 未授權玩家無法放置或破壞方塊
2. 未授權玩家無法使用石質壓力板或石質按鈕
3. 生物無法觸發石質壓力板
4. 苦力怕無法在保護區內破壞方塊

**❌ 不受保護**
1. 未授權玩家可以放置 TNT，且會立即點燃（可設定）
2. 未授權玩家仍可使用木質壓力板／按鈕、箱子與盔甲座
3. TNT 可破壞保護區內的方塊（可設定）

## 指令與權限

- `/kill` — 讓玩家可以自殺（若不慎被困在保護區內時很有用）
- 不需要額外的權限系統
- 創造模式下的管理員（OP）會自動略過保護限制

## 安裝方式

1. 下載插件的 `.jar` 檔案
2. 放入伺服器的 `plugins/` 資料夾
3. 重新啟動伺服器
4. 編輯 `config.yml` 與語言檔（`locales/` 資料夾）來調整設定

## 語言檔

語言檔位於 `plugins/Cupboard/locales/` 資料夾內。將 `config.yml` 的 `locale` 欄位設定為檔名（不含 `.yml`）即可切換語言。內建語言檔如下：

| 語系代碼 | 語言 |
|---|---|
| `en` | 英文（預設） |
| `zh` | 中文（簡體） |
| `zh_TW` | 中文（台灣，繁體） |
| `zh_HK` | 中文（香港，繁體） |

若要新增其他語言翻譯，只需在 `locales/` 資料夾中建立一個新的 `.yml` 檔案，使用相同的鍵值（key），再將 `locale` 設定為該檔案名稱即可。

## 相容性

已測試並相容於 Spigot、Bukkit、Paper、Purpur、Leaves、Folia 與 Luminol。

- 以 Minecraft 1.20 API 為基準（1.20.x 及之後版本）
- 需要 Java 17 以上版本
- Folia 透過相容排程層（compatibility scheduler layer）提供支援
- Purpur 為 Paper 的分支版本，共用相同的 Bukkit/Paper API 介面

## 授權條款

MIT、GPL-3.0 等（依實際專案授權為準）。

## 致謝

由 [YourNameHere] 開發。
靈感來自 Rust 遊戲中的領地保護系統。
