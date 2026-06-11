# Multi-Card Vault

Multi-Card Vault 是一个本地优先的多卡门限加密保险库项目。该上下文用于统一保险库、卡片分片、用户密码与设备侧密钥之间的领域语言。

## Language

**Vault**:
用户本地保存的加密数据集合。
_Avoid_: password manager, cloud vault

**Vault Blob**:
保存 **Vault** 内容的密文记录。
_Avoid_: backup, plaintext file

**Vault Plaintext**:
**Vault** 解锁后在当前会话内可编辑的明文内容。
_Avoid_: stored data, database row

**Card Payload**:
写入 **Card**、用于承载加密 **Share** 及其认证上下文的序列化数据。
_Avoid_: NFC dump, access card data

**Card**:
承载一个加密分片 payload 的 NFC 标签。
_Avoid_: access card, cloned card

**Share**:
由门限分片方案生成、单独不能恢复完整秘密的密钥分片。
_Avoid_: password fragment, full key

**Threshold**:
恢复秘密所需的最少有效 **Share** 数量。
_Avoid_: scan count

**Total**:
同一组门限方案中生成的 **Share** 总数量。
_Avoid_: card capacity

**Scheme ID**:
标识同一批门限分片方案的唯一 ID。
_Avoid_: vault version

**Vault ID**:
标识一个 **Vault** 的唯一 ID。
_Avoid_: database id, display name

**Device Secret**:
由 Android 设备侧保护的、参与解锁当前 **Vault** 的秘密。
_Avoid_: recovery key, user password

**User Password**:
用户记忆并输入、参与解锁当前 **Vault** 的主密码。
_Avoid_: PIN, saved password

**Final Key**:
由足够数量的 **Share**、**User Password** 和 **Device Secret** 共同派生出的数据解密能力。
_Avoid_: master password, card key

**Cross-Device Recovery**:
在没有原设备 **Device Secret** 的情况下，于另一台设备恢复 **Vault** 解锁能力。
_Avoid_: backup import, card unlock

## Relationships

- 一个 **Vault** 有且只有一个 **Vault ID**。
- 一个 **Vault Blob** 属于一个 **Vault**。
- 一个 **Vault Blob** 加密一个 **Vault Plaintext**。
- 一个 **Scheme ID** 对应一批 **Share**。
- 一个 **Card** 承载一个 **Card Payload**。
- 一个 **Card Payload** 承载一个加密后的 **Share**。
- 一个 **Vault** 的解锁需要至少 **Threshold** 个不同 **Share**。
- **Threshold** 必须小于或等于 **Total**。
- **Final Key** 依赖足够数量的 **Share**、一个 **User Password** 和一个 **Device Secret**。
- **Cross-Device Recovery** 不是普通 **Vault Blob** 导入；它需要额外恢复模型。

## Example Dialogue

> **Dev:** “用户刷够三张 Card 之后，我们能直接打开 Vault 吗？”
> **Domain expert:** “不能。三张 Card 只提供足够的 Share，还必须结合 User Password 和 Device Secret 才能得到 Final Key。”

## Flagged Ambiguities

- “卡片顺序”不是安全概念，只是交互流程；安全条件是不同有效 **Share** 的数量达到 **Threshold**。
- **Card** 指本应用写入的 NFC 标签，不指第三方门禁卡、克隆卡或未授权卡片。
- **Device Secret** 不是跨设备恢复能力；如果恢复模型需要跨设备，应另行定义新的恢复概念。
- “备份导入”不等于 **Cross-Device Recovery**；MVP 只承诺当前设备上的本地保险库语义。
