# Card Lifecycle Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first post-MVP Card Lifecycle Foundation so MCV can inspect, inventory, verify, reissue, and recover CUID Card Sets without persisting complete Card Payloads locally.

**Architecture:** Rust remains the protocol source of truth and gains a non-sensitive Card Payload inspection API. Android adds safe threshold presets, rebuildable Card Inventory metadata in Room, focused card lifecycle use cases, and simple functional UI wiring on top of the existing NFC flow. Full Card Payloads stay in current process memory only.

**Tech Stack:** Rust, UniFFI, Kotlin, Jetpack Compose, Room, DataStore, Android `MifareClassic`, JUnit.

---

## File Structure

### Rust And Bindings

- Modify `crates/mcv-core/src/lib.rs`: add `CardPayloadInspection` and `inspect_card_payload`.
- Modify `crates/mcv-uniffi/src/lib.rs`: expose the inspection record/function through UniFFI.
- Regenerate `bindings/kotlin/app/multicardvault/uniffi/mcv_uniffi.kt`.
- Modify `android/app/src/main/java/app/multicardvault/core/RustMcvCore.kt`: wrap inspection in the Kotlin core boundary.
- Modify `android/app/src/test/java/app/multicardvault/core/RustMcvCoreTest.kt`: cover inspection through generated bindings.

### Android Safe Presets

- Create `android/app/src/main/java/app/multicardvault/features/create/SafeThresholdPreset.kt`: allowed presets and validation.
- Modify `android/app/src/main/java/app/multicardvault/features/create/CreateVaultUseCase.kt`: reject non-preset creation settings.
- Modify `android/app/src/main/java/app/multicardvault/settings/AppSettingsRepository.kt`: migrate unsafe stored defaults to `3-of-5`.
- Modify `android/app/src/test/java/app/multicardvault/settings/DataStoreAppSettingsRepositoryTest.kt`.
- Modify `android/app/src/test/java/app/multicardvault/features/create/CreateVaultUseCaseTest.kt`.

### Android Card Inventory

- Modify `android/app/src/main/java/app/multicardvault/data/VaultEntities.kt`: add Card Inventory entity/record mappings or split to new file if the file becomes noisy.
- Create `android/app/src/main/java/app/multicardvault/data/CardInventoryDao.kt`.
- Create `android/app/src/main/java/app/multicardvault/data/CardInventoryRepository.kt`.
- Modify `android/app/src/main/java/app/multicardvault/data/McvDatabase.kt`: add entity, DAO, version bump, explicit migration, and remove destructive migration.
- Create `android/app/src/test/java/app/multicardvault/data/CardInventoryRepositoryTest.kt`.
- Create `android/app/src/test/java/app/multicardvault/data/McvDatabaseMigrationTest.kt` if local JVM Room migration testing is available after adding test dependencies.

### Android Card Lifecycle Domain

- Create `android/app/src/main/java/app/multicardvault/features/cards/CardInspectionModels.kt`.
- Create `android/app/src/main/java/app/multicardvault/features/cards/InspectCardUseCase.kt`.
- Create `android/app/src/main/java/app/multicardvault/features/cards/VerifyCardSetUseCase.kt`.
- Create `android/app/src/main/java/app/multicardvault/features/cards/StartCardSetReissueUseCase.kt`.
- Create `android/app/src/main/java/app/multicardvault/features/cards/RecoverInterruptedReissueUseCase.kt`.
- Create matching tests under `android/app/src/test/java/app/multicardvault/features/cards/`.

### Android UI Wiring

- Modify `android/app/src/main/java/app/multicardvault/features/create/CreateVaultViewModel.kt`: add minimal card lifecycle scan/reissue states and delegate logic to card use cases.
- Modify `android/app/src/main/java/app/multicardvault/MainActivity.kt`: inject Card Inventory dependencies and show simple controls/status.
- Modify `android/app/src/test/java/app/multicardvault/features/create/CreateVaultViewModelTest.kt`: cover basic card lifecycle state transitions.

### Docs

- Modify `docs/android-architecture.md`.
- Modify `docs/recovery-model.md`.
- Modify `docs/known-issues.md`.
- Modify `docs/manual-nfc-test-record.md`.
- Modify `README.md` if Current Status changes.

---

### Task 1: Rust Card Payload Inspection

**Files:**
- Modify: `crates/mcv-core/src/lib.rs`
- Modify: `crates/mcv-uniffi/src/lib.rs`
- Regenerate: `bindings/kotlin/app/multicardvault/uniffi/mcv_uniffi.kt`
- Modify: `android/app/src/main/java/app/multicardvault/core/RustMcvCore.kt`
- Test: `android/app/src/test/java/app/multicardvault/core/RustMcvCoreTest.kt`

- [ ] **Step 1: Add failing Rust core tests**

Add tests in `crates/mcv-core/src/lib.rs`:

```rust
#[test]
fn inspect_card_payload_returns_only_header_metadata() -> Result<(), McvError> {
    let mut rng = ChaCha20Rng::seed_from_u64(42);
    let created = create_vault_with_rng(
        CreateVaultRequest {
            password: "passphrase".to_owned(),
            threshold: 2,
            total: 3,
            initial_plaintext: empty_vault_plaintext()?,
        },
        KdfParams::TEST,
        &mut rng,
    )?;

    let inspection = inspect_card_payload(&created.card_payloads[0])?;

    assert_eq!(inspection.vault_id, created.vault_id);
    assert_eq!(inspection.scheme_id, created.scheme_id);
    assert_eq!(inspection.threshold, 2);
    assert_eq!(inspection.total, 3);
    assert_eq!(inspection.share_index, 1);
    assert_eq!(inspection.kdf_id, KDF_ARGON2ID_V1);
    assert_eq!(inspection.aead_id, AEAD_XCHACHA20_POLY1305_V1);
    assert_eq!(inspection.format_version, mcv_format::FORMAT_VERSION_V1);
    Ok(())
}

#[test]
fn inspect_card_payload_rejects_malformed_payload() {
    assert_eq!(
        inspect_card_payload(b"not a card"),
        Err(McvError::InvalidCardPayload)
    );
}
```

- [ ] **Step 2: Run failing Rust core tests**

Run:

```bash
cargo test -p mcv-core inspect_card_payload
```

Expected: fail because `CardPayloadInspection` and `inspect_card_payload` do not exist.

- [ ] **Step 3: Implement Rust inspection model**

Add near existing request/response structs in `crates/mcv-core/src/lib.rs`:

```rust
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CardPayloadInspection {
    pub vault_id: Vec<u8>,
    pub scheme_id: Vec<u8>,
    pub threshold: u8,
    pub total: u8,
    pub share_index: u8,
    pub kdf_id: u8,
    pub aead_id: u8,
    pub format_version: u8,
}
```

Add public function:

```rust
pub fn inspect_card_payload(bytes: &[u8]) -> Result<CardPayloadInspection, McvError> {
    let card = CardPayloadV1::decode(bytes).map_err(map_card_format_error)?;
    validate_algorithm_ids(card.kdf_id, card.aead_id, SSS_SHAMIR_GF256_V1)?;
    Ok(CardPayloadInspection {
        vault_id: card.vault_id,
        scheme_id: card.scheme_id,
        threshold: card.threshold,
        total: card.total,
        share_index: card.share_index,
        kdf_id: card.kdf_id,
        aead_id: card.aead_id,
        format_version: mcv_format::FORMAT_VERSION_V1,
    })
}
```

- [ ] **Step 4: Run Rust core tests**

Run:

```bash
cargo test -p mcv-core inspect_card_payload
cargo test -p mcv-core
```

Expected: all selected tests pass.

- [ ] **Step 5: Add UniFFI inspection record and tests**

In `crates/mcv-uniffi/src/lib.rs`, add:

```rust
#[derive(Clone, Debug, Eq, PartialEq, uniffi::Record)]
pub struct CardPayloadInspection {
    pub vault_id: Vec<u8>,
    pub scheme_id: Vec<u8>,
    pub threshold: u8,
    pub total: u8,
    pub share_index: u8,
    pub kdf_id: u8,
    pub aead_id: u8,
    pub format_version: u8,
}

#[uniffi::export]
pub fn inspect_card_payload(card_payload: Vec<u8>) -> Result<CardPayloadInspection, McvFfiError> {
    let inspection = mcv_core::inspect_card_payload(&card_payload).map_err(McvFfiError::from)?;
    Ok(inspection.into())
}
```

Add conversion:

```rust
impl From<mcv_core::CardPayloadInspection> for CardPayloadInspection {
    fn from(value: mcv_core::CardPayloadInspection) -> Self {
        Self {
            vault_id: value.vault_id,
            scheme_id: value.scheme_id,
            threshold: value.threshold,
            total: value.total,
            share_index: value.share_index,
            kdf_id: value.kdf_id,
            aead_id: value.aead_id,
            format_version: value.format_version,
        }
    }
}
```

Add a UniFFI test that creates a vault and inspects the first returned card payload.

- [ ] **Step 6: Run UniFFI tests and regenerate bindings**

Run:

```bash
cargo test -p mcv-uniffi inspect_card_payload
cargo build -p mcv-uniffi
cargo run -p mcv-bindgen -- target/debug/libmcv_uniffi.so bindings/kotlin
```

Expected: tests pass and generated Kotlin binding changes include `inspectCardPayload`.

- [ ] **Step 7: Add Kotlin core wrapper and failing test**

In `RustMcvCore.kt`, add:

```kotlin
data class RustCardPayloadInspection(
    val vaultId: ByteArray,
    val schemeId: ByteArray,
    val threshold: Int,
    val total: Int,
    val shareIndex: Int,
    val kdfId: Int,
    val aeadId: Int,
    val formatVersion: Int,
)
```

Add `fun inspectCardPayload(cardPayload: ByteArray): RustCardPayloadInspection` to `McvCore`.

Add a failing test in `RustMcvCoreTest.kt`:

```kotlin
@Test
fun inspectCardPayloadThroughUniffiReturnsNonSensitiveMetadata() {
    val created = core.createVault(
        password = "correct horse battery staple",
        threshold = 2,
        total = 3,
        initialPlaintext = core.emptyVaultPlaintext(),
    )

    val inspection = core.inspectCardPayload(created.cardPayloads.first())

    assertEquals(created.vaultId.toList(), inspection.vaultId.toList())
    assertEquals(created.schemeId.toList(), inspection.schemeId.toList())
    assertEquals(2, inspection.threshold)
    assertEquals(3, inspection.total)
    assertEquals(1, inspection.shareIndex)
}
```

- [ ] **Step 8: Implement Kotlin wrapper**

Import generated `inspectCardPayload` and map `UByte` fields to `Int`. Catch `McvFfiException` and wrap it in `McvCoreException("Rust core rejected card payload inspection", error)`.

- [ ] **Step 9: Run Android core test**

Run:

```bash
./gradlew -p android testDebugUnitTest --tests app.multicardvault.core.RustMcvCoreTest
```

Expected: pass.

- [ ] **Step 10: Commit**

```bash
git add "crates/mcv-core/src/lib.rs" "crates/mcv-uniffi/src/lib.rs" "bindings/kotlin/app/multicardvault/uniffi/mcv_uniffi.kt" "android/app/src/main/java/app/multicardvault/core/RustMcvCore.kt" "android/app/src/test/java/app/multicardvault/core/RustMcvCoreTest.kt"
git commit -m "feat: inspect card payload metadata"
```

---

### Task 2: Safe Threshold Presets

**Files:**
- Create: `android/app/src/main/java/app/multicardvault/features/create/SafeThresholdPreset.kt`
- Modify: `android/app/src/main/java/app/multicardvault/features/create/CreateVaultUseCase.kt`
- Modify: `android/app/src/main/java/app/multicardvault/settings/AppSettingsRepository.kt`
- Modify: `android/app/src/test/java/app/multicardvault/settings/DataStoreAppSettingsRepositoryTest.kt`
- Modify: `android/app/src/test/java/app/multicardvault/features/create/CreateVaultUseCaseTest.kt`
- Modify: fake `McvCore` implementations that now include `inspectCardPayload`

- [ ] **Step 1: Write failing Safe Threshold tests**

Create `SafeThresholdPresetTest.kt`:

```kotlin
class SafeThresholdPresetTest {
    @Test
    fun supportedPresetsAreTwoOfThreeAndThreeOfFive() {
        assertTrue(SafeThresholdPreset.isAllowed(2, 3))
        assertTrue(SafeThresholdPreset.isAllowed(3, 5))
        assertFalse(SafeThresholdPreset.isAllowed(4, 5))
        assertFalse(SafeThresholdPreset.isAllowed(1, 1))
    }

    @Test
    fun unsafePairFallsBackToDefault() {
        assertEquals(SafeThresholdPreset.ThreeOfFive, SafeThresholdPreset.from(4, 5))
    }
}
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
./gradlew -p android testDebugUnitTest --tests app.multicardvault.features.create.SafeThresholdPresetTest
```

Expected: fail because `SafeThresholdPreset` does not exist.

- [ ] **Step 3: Implement SafeThresholdPreset**

Create:

```kotlin
enum class SafeThresholdPreset(
    val threshold: Int,
    val total: Int,
    val label: String,
) {
    TwoOfThree(2, 3, "2-of-3"),
    ThreeOfFive(3, 5, "3-of-5");

    companion object {
        val Default = ThreeOfFive

        fun isAllowed(threshold: Int, total: Int): Boolean =
            entries.any { it.threshold == threshold && it.total == total }

        fun from(threshold: Int, total: Int): SafeThresholdPreset =
            entries.firstOrNull { it.threshold == threshold && it.total == total } ?: Default
    }
}
```

- [ ] **Step 4: Run preset tests**

Run:

```bash
./gradlew -p android testDebugUnitTest --tests app.multicardvault.features.create.SafeThresholdPresetTest
```

Expected: pass.

- [ ] **Step 5: Reject unsafe creation settings**

Update `CreateVaultUseCase` so `invoke` requires `SafeThresholdPreset.isAllowed(threshold, total)` before calling Rust.

Add test in `CreateVaultUseCaseTest.kt`:

```kotlin
@Test
fun createVaultRejectsUnsafeThresholdPreset() =
    runTest {
        val useCase = CreateVaultUseCase(FakeMcvCore(), FakeVaultRepository())

        val result = runCatching {
            useCase("Primary", "passphrase", threshold = 4, total = 5)
        }

        assertTrue(result.isFailure)
    }
```

- [ ] **Step 6: Migrate DataStore defaults**

Update `DataStoreAppSettingsRepository`:

```kotlin
val preset = SafeThresholdPreset.from(
    threshold = preferences[DefaultThresholdKey] ?: SafeThresholdPreset.Default.threshold,
    total = preferences[DefaultTotalKey] ?: SafeThresholdPreset.Default.total,
)
AppSettings(
    defaultThreshold = preset.threshold,
    defaultTotal = preset.total,
    ...
)
```

Change setters so setting threshold/total stores only a matching preset. Prefer adding:

```kotlin
suspend fun setDefaultThresholdPreset(preset: SafeThresholdPreset)
```

Keep existing `setDefaultThreshold` and `setDefaultTotal` only as compatibility wrappers that resolve through `SafeThresholdPreset.from`.

- [ ] **Step 7: Update settings tests**

Replace `thresholdIsClampedToTotal` with:

```kotlin
@Test
fun unsafeThresholdSettingsFallbackToDefaultPreset() =
    runTest {
        val repository = repository(backgroundScope)

        repository.setDefaultTotal(5)
        repository.setDefaultThreshold(4)

        val settings = repository.settings.first()
        assertEquals(3, settings.defaultThreshold)
        assertEquals(5, settings.defaultTotal)
    }
```

- [ ] **Step 8: Run focused Android tests**

Run:

```bash
./gradlew -p android testDebugUnitTest --tests app.multicardvault.features.create.CreateVaultUseCaseTest --tests app.multicardvault.settings.DataStoreAppSettingsRepositoryTest
```

Expected: pass.

- [ ] **Step 9: Commit**

```bash
git add "android/app/src/main/java/app/multicardvault/features/create" "android/app/src/main/java/app/multicardvault/settings/AppSettingsRepository.kt" "android/app/src/test/java/app/multicardvault/features/create" "android/app/src/test/java/app/multicardvault/settings"
git commit -m "feat: add safe threshold presets"
```

---

### Task 3: Card Inventory Persistence

**Files:**
- Modify: `android/app/src/main/java/app/multicardvault/data/VaultEntities.kt`
- Create: `android/app/src/main/java/app/multicardvault/data/CardInventoryDao.kt`
- Create: `android/app/src/main/java/app/multicardvault/data/CardInventoryRepository.kt`
- Modify: `android/app/src/main/java/app/multicardvault/data/McvDatabase.kt`
- Modify: `android/app/build.gradle.kts` only if a local Room migration test dependency is required.
- Create: `android/app/src/test/java/app/multicardvault/data/CardInventoryRepositoryTest.kt`
- Optional Create: `android/app/src/test/java/app/multicardvault/data/McvDatabaseMigrationTest.kt`

- [ ] **Step 1: Write failing repository tests**

Create `CardInventoryRepositoryTest.kt` using a fake DAO if local Room tests are too expensive:

```kotlin
@Test
fun upsertCardInventoryKeepsOneRecordPerVaultSchemeAndShareIndex() =
    runTest {
        val repository = FakeCardInventoryRepository()
        val first = cardRecord(label = "Card 1", lastSeenAt = 1)
        val second = first.copy(label = "Primary Card", lastSeenAt = 2)

        repository.upsert(first)
        repository.upsert(second)

        val records = repository.listForVault(first.vaultIdHex)
        assertEquals(1, records.size)
        assertEquals("Primary Card", records.single().label)
        assertEquals(2, records.single().lastSeenAt)
    }
```

Prefer testing the real `RoomCardInventoryRepository` if Robolectric/Room local unit testing is already reliable after dependency changes.

- [ ] **Step 2: Run failing repository tests**

Run:

```bash
./gradlew -p android testDebugUnitTest --tests app.multicardvault.data.CardInventoryRepositoryTest
```

Expected: fail because inventory repository does not exist.

- [ ] **Step 3: Add inventory entity and models**

Add:

```kotlin
enum class CardInventoryStatus {
    Current,
    OldScheme,
    Duplicate,
    WrongVault,
    Unreadable,
    Unknown,
}

@Entity(
    tableName = "card_inventory",
    indices = [
        Index(
            value = ["vaultIdHex", "schemeIdHex", "shareIndex"],
            unique = true,
        ),
    ],
)
data class CardInventoryEntity(
    @PrimaryKey val id: String,
    val vaultIdHex: String,
    val schemeIdHex: String,
    val shareIndex: Int,
    val threshold: Int,
    val total: Int,
    val label: String,
    val status: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val lastCheckMessage: String,
)
```

Store `status` as `String` in Room and map it to/from `CardInventoryStatus` at repository boundaries. Do not add a type converter unless another Room converter is already needed.

- [ ] **Step 4: Add DAO and repository**

Create DAO:

```kotlin
@Dao
interface CardInventoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(record: CardInventoryEntity)

    @Query("SELECT * FROM card_inventory WHERE vaultIdHex = :vaultIdHex ORDER BY shareIndex ASC")
    suspend fun listForVault(vaultIdHex: String): List<CardInventoryEntity>

    @Query("SELECT * FROM card_inventory WHERE vaultIdHex = :vaultIdHex AND schemeIdHex = :schemeIdHex ORDER BY shareIndex ASC")
    suspend fun listForCardSet(vaultIdHex: String, schemeIdHex: String): List<CardInventoryEntity>

    @Query("UPDATE card_inventory SET status = :status WHERE vaultIdHex = :vaultIdHex AND schemeIdHex != :currentSchemeIdHex")
    suspend fun markOtherSchemes(vaultIdHex: String, currentSchemeIdHex: String, status: String): Int
}
```

Create repository interface plus Room implementation.

- [ ] **Step 5: Add explicit Room migration**

Update `McvDatabase` to version `3`, add `CardInventoryEntity`, expose `cardInventoryDao()`, and replace `.fallbackToDestructiveMigration()` with `.addMigrations(MIGRATION_2_3)`.

Migration SQL should create the new table and unique index without modifying `vaults`.

- [ ] **Step 6: Add migration test if feasible**

If local JVM Room migration test is feasible, add dependencies and test that a v2 database with one Vault row migrates to v3 while preserving that row and creating `card_inventory`.

Run if `McvDatabaseMigrationTest` was implemented:

```bash
./gradlew -p android testDebugUnitTest --tests app.multicardvault.data.McvDatabaseMigrationTest
```

If local migration test is not feasible without Android instrumentation, document that limitation in the plan execution notes and still add repository tests.

- [ ] **Step 7: Run focused tests**

Run:

```bash
./gradlew -p android testDebugUnitTest --tests app.multicardvault.data.CardInventoryRepositoryTest
```

Expected: repository tests pass. Also run the migration test command from Step 6 if that test was implemented.

- [ ] **Step 8: Commit**

```bash
git add "android/app/src/main/java/app/multicardvault/data" "android/app/src/test/java/app/multicardvault/data" "android/app/build.gradle.kts"
git commit -m "feat: persist card inventory metadata"
```

---

### Task 4: Card Verification Use Cases

**Files:**
- Create: `android/app/src/main/java/app/multicardvault/features/cards/CardInspectionModels.kt`
- Create: `android/app/src/main/java/app/multicardvault/features/cards/InspectCardUseCase.kt`
- Create: `android/app/src/main/java/app/multicardvault/features/cards/VerifyCardSetUseCase.kt`
- Create: `android/app/src/test/java/app/multicardvault/features/cards/InspectCardUseCaseTest.kt`
- Create: `android/app/src/test/java/app/multicardvault/features/cards/VerifyCardSetUseCaseTest.kt`
- Modify: test fakes implementing `McvCore`

- [ ] **Step 1: Write failing inspection use case tests**

Create:

```kotlin
@Test
fun inspectCardMapsRustMetadataToStableHex() {
    val useCase = InspectCardUseCase(FakeMcvCore())

    val result = useCase(byteArrayOf(1, 2, 3))

    assertEquals("01010101010101010101010101010101", result.vaultIdHex)
    assertEquals("02020202020202020202020202020202", result.schemeIdHex)
    assertEquals(1, result.shareIndex)
}
```

- [ ] **Step 2: Implement Card inspection models and use case**

Add:

```kotlin
data class CardPayloadInspectionSummary(
    val vaultIdHex: String,
    val schemeIdHex: String,
    val threshold: Int,
    val total: Int,
    val shareIndex: Int,
    val formatVersion: Int,
)
```

Use `McvCore.inspectCardPayload` and `toStableHex()`.

- [ ] **Step 3: Write failing verification classification tests**

Cover:

```kotlin
currentCardIsAccepted()
oldSchemeCardIsClassified()
wrongVaultCardIsClassified()
duplicateShareIndexIsClassified()
invalidCardIsUnreadable()
```

Test input should be `SavedVaultSummary`, known current scheme ID, current inventory records, and inspected payload summary.

- [ ] **Step 4: Implement VerifyCardSetUseCase**

Define a result:

```kotlin
data class CardVerificationResult(
    val status: CardInventoryStatus,
    val message: String,
    val inspection: CardPayloadInspectionSummary?,
)
```

Rules:

- If inspection fails, status `Unreadable`.
- If Vault ID differs from selected Vault, status `WrongVault`.
- If Scheme ID differs from current known Scheme ID, status `OldScheme`.
- If same Share Index already exists in current scanned set, status `Duplicate`.
- Otherwise status `Current`.

Persist successful inspections through `CardInventoryRepository`.

- [ ] **Step 5: Run card use case tests**

Run:

```bash
./gradlew -p android testDebugUnitTest --tests app.multicardvault.features.cards.InspectCardUseCaseTest --tests app.multicardvault.features.cards.VerifyCardSetUseCaseTest
```

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add "android/app/src/main/java/app/multicardvault/features/cards" "android/app/src/test/java/app/multicardvault/features/cards"
git commit -m "feat: verify card set membership"
```

---

### Task 5: Card Set Reissue Use Case

**Files:**
- Create: `android/app/src/main/java/app/multicardvault/features/cards/StartCardSetReissueUseCase.kt`
- Create: `android/app/src/test/java/app/multicardvault/features/cards/StartCardSetReissueUseCaseTest.kt`
- Modify: `android/app/src/main/java/app/multicardvault/features/vault/UpdateVaultUseCase.kt` only if needed to expose a reusable lower-level operation.

- [ ] **Step 1: Write failing reissue tests**

Test that reissue uses existing unlocked entries and returns a complete replacement Card Set:

```kotlin
@Test
fun reissueReturnsCompleteReplacementCardSet() =
    runTest {
        val useCase = StartCardSetReissueUseCase(
            core = FakeMcvCore(),
            cardInventoryRepository = FakeCardInventoryRepository(),
        )

        val result = useCase(
            vaultIdHex = "01010101010101010101010101010101",
            password = "passphrase",
            cardPayloads = listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3)),
            entries = emptyList(),
            updatedAt = 10,
        )

        assertEquals(5, result.cardPayloads.size)
        assertTrue(result.cardPayloads.all { it.isNotEmpty() })
    }
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
./gradlew -p android testDebugUnitTest --tests app.multicardvault.features.cards.StartCardSetReissueUseCaseTest
```

Expected: fail because use case does not exist.

- [ ] **Step 3: Implement StartCardSetReissueUseCase**

Use `McvCore.encodeVaultPlaintext` and `McvCore.updateVault` directly, or call a refactored lower-level method from `UpdateVaultUseCase`.

Keep responsibilities narrow:

- Input: password, threshold card payloads, current entries, updated timestamp.
- Output: replacement Card Payloads and plaintext size.
- Side effect: mark old card inventory schemes as `OldScheme` only after the write flow completes, not at generation time.

- [ ] **Step 4: Add completion method for inventory**

Add a repository method:

```kotlin
suspend fun markCurrentCardSet(vaultIdHex: String, schemeIdHex: String)
```

This should mark matching records as `Current` and other scheme records for the same Vault as `OldScheme`.

- [ ] **Step 5: Run reissue tests**

Run:

```bash
./gradlew -p android testDebugUnitTest --tests app.multicardvault.features.cards.StartCardSetReissueUseCaseTest
```

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add "android/app/src/main/java/app/multicardvault/features/cards" "android/app/src/main/java/app/multicardvault/data" "android/app/src/test/java/app/multicardvault/features/cards"
git commit -m "feat: reissue card sets"
```

---

### Task 6: Interrupted Reissue Recovery

**Files:**
- Create: `android/app/src/main/java/app/multicardvault/features/cards/RecoverInterruptedReissueUseCase.kt`
- Create: `android/app/src/test/java/app/multicardvault/features/cards/RecoverInterruptedReissueUseCaseTest.kt`

- [ ] **Step 1: Write failing grouping tests**

Cover:

```kotlin
@Test
fun groupingDoesNotRecoverUntilSchemeReachesThreshold()

@Test
fun groupingChoosesSchemeThatReachedThreshold()

@Test
fun duplicateShareIndexDoesNotIncreaseProgress()
```

Use simple in-memory scanned payload records:

```kotlin
data class ScannedCardPayload(
    val payload: ByteArray,
    val inspection: CardPayloadInspectionSummary,
)
```

- [ ] **Step 2: Implement recovery grouping**

`RecoverInterruptedReissueUseCase` should:

- Inspect each scanned payload.
- Group by `vaultIdHex + schemeIdHex`.
- Keep one payload per Share Index per group.
- Return `NeedsMoreCards(readCount, threshold)` until a group reaches threshold.
- Return `ReadyToUnlock(vaultIdHex, schemeIdHex, cardPayloads)` when ready.

- [ ] **Step 3: Run recovery tests**

Run:

```bash
./gradlew -p android testDebugUnitTest --tests app.multicardvault.features.cards.RecoverInterruptedReissueUseCaseTest
```

Expected: pass.

- [ ] **Step 4: Commit**

```bash
git add "android/app/src/main/java/app/multicardvault/features/cards/RecoverInterruptedReissueUseCase.kt" "android/app/src/test/java/app/multicardvault/features/cards/RecoverInterruptedReissueUseCaseTest.kt"
git commit -m "feat: recover interrupted card reissue"
```

---

### Task 7: Functional UI Wiring

**Files:**
- Modify: `android/app/src/main/java/app/multicardvault/MainActivity.kt`
- Modify: `android/app/src/main/java/app/multicardvault/features/create/CreateVaultViewModel.kt`
- Modify: `android/app/src/test/java/app/multicardvault/features/create/CreateVaultViewModelTest.kt`

- [ ] **Step 1: Add failing ViewModel tests**

Add tests for:

```kotlin
verifyCardScanUpdatesCardInventoryState()
startCardSetReissueMovesToWritingCards()
interruptedRecoveryKeepsReadingUntilAGroupReachesThreshold()
```

Keep fakes small. Update fake `McvCore` with `inspectCardPayload`.

- [ ] **Step 2: Add card lifecycle UI state**

Extend state with minimal variants:

```kotlin
data class VerifyingCard(...)
data class CardVerified(...)
data class RecoveringInterruptedReissue(...)
```

If adding to `CreateVaultUiState` becomes too noisy, introduce a separate `CardLifecycleUiState` flow inside the same ViewModel.

- [ ] **Step 3: Wire ViewModel use cases**

Add methods:

```kotlin
fun startVerifyCard(vault: SavedVaultSummary)
fun startCardSetReissue()
fun startInterruptedReissueRecovery()
```

Route `nextNfcCommand()` to `NfcCommand.Read` for verify/recovery modes.

Keep existing create/unlock/update behavior unchanged.

- [ ] **Step 4: Wire MainActivity dependencies**

Instantiate:

- `RoomCardInventoryRepository(database.cardInventoryDao())`
- `InspectCardUseCase(core)`
- `VerifyCardSetUseCase(...)`
- `StartCardSetReissueUseCase(...)`
- `RecoverInterruptedReissueUseCase(...)`

Add simple Compose controls:

- "校验卡片"
- "重发整套卡"
- "恢复中断重发"

No visual polish required; preserve Material3 style already used.

- [ ] **Step 5: Run ViewModel tests**

Run:

```bash
./gradlew -p android testDebugUnitTest --tests app.multicardvault.features.create.CreateVaultViewModelTest
```

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add "android/app/src/main/java/app/multicardvault/MainActivity.kt" "android/app/src/main/java/app/multicardvault/features/create/CreateVaultViewModel.kt" "android/app/src/test/java/app/multicardvault/features/create/CreateVaultViewModelTest.kt"
git commit -m "feat: wire card lifecycle flows"
```

---

### Task 8: Documentation, Manual Matrix, And Full Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/android-architecture.md`
- Modify: `docs/recovery-model.md`
- Modify: `docs/known-issues.md`
- Modify: `docs/manual-nfc-test-record.md`

- [ ] **Step 1: Update docs**

Update docs to state:

- Card Lifecycle Foundation is implemented.
- Safe Threshold Presets are the only offered new-Vault threshold choices.
- Card Inventory is local non-recovery metadata.
- Card Set Reissue replaces the whole Card Set.
- Interrupted Reissue Recovery scans Cards and does not resume from cached payloads.

- [ ] **Step 2: Update manual NFC matrix**

Add cases:

- Scan and label all Cards in a Card Set.
- Verify a current Card.
- Verify an old Card after reissue.
- Replace one Card through Card Set Reissue.
- Interrupt reissue after one card and recover.
- Interrupt reissue near the threshold boundary and recover.
- Scan mixed old/new Cards and converge to one current Card Set.

- [ ] **Step 3: Run Rust verification**

Run:

```bash
cargo fmt --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
```

Expected: all pass.

- [ ] **Step 4: Run Android verification**

Run:

```bash
./gradlew -p android ktlintCheck
./gradlew -p android detekt
./gradlew -p android testDebugUnitTest
./gradlew -p android assembleDebug
```

Expected: all pass.

- [ ] **Step 5: Confirm no complete Card Payload persistence**

Run:

```bash
rg -n "cardPayloads|CardPayload|payload" "android/app/src/main/java/app/multicardvault/data" "android/app/src/main/java/app/multicardvault/settings"
```

Expected: no Room/DataStore field stores full Card Payload bytes. Card Inventory may store only non-sensitive IDs, share index, threshold, total, label, status, and timestamps.

- [ ] **Step 6: Commit docs and final polish**

```bash
git add "README.md" "docs/android-architecture.md" "docs/recovery-model.md" "docs/known-issues.md" "docs/manual-nfc-test-record.md"
git commit -m "docs: document card lifecycle foundation"
```

---

## Final Completion Checklist

- [ ] `cargo fmt --check` passes.
- [ ] `cargo clippy --workspace --all-targets -- -D warnings` passes.
- [ ] `cargo test --workspace` passes.
- [ ] `./gradlew -p android ktlintCheck` passes.
- [ ] `./gradlew -p android detekt` passes.
- [ ] `./gradlew -p android testDebugUnitTest` passes.
- [ ] `./gradlew -p android assembleDebug` passes.
- [ ] Manual NFC matrix is updated for the new flows.
- [ ] No complete Card Payload is persisted in Room or DataStore.
