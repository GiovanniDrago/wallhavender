package xyz.attacktive.wallhavend

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the fix for issue #146: the file holding the encrypted API key must stay out of both
 * backup paths, and the manifest must actually point at the rules that exclude it.
 */
class ApiKeyBackupRulesTest {
	private val securePreferencesFile = "wallhavend_secure_prefs.xml"

	@Test
	fun `auto-backup rules exclude the encrypted key file`() {
		val rules = File("src/main/res/xml/backup_rules.xml").readText()

		assertTrue(rules.substringAfter("<full-backup-content>").contains(securePreferencesFile))
	}

	@Test
	fun `data extraction rules exclude the encrypted key file from cloud backup and transfer`() {
		val rules = File("src/main/res/xml/data_extraction_rules.xml").readText()
		val cloudBackup = rules.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
		val deviceTransfer = rules.substringAfter("<device-transfer>").substringBefore("</device-transfer>")

		assertTrue(cloudBackup.contains(securePreferencesFile))
		assertTrue(deviceTransfer.contains(securePreferencesFile))
	}

	@Test
	fun `the manifest references the backup rules`() {
		val manifest = File("src/main/AndroidManifest.xml").readText()

		assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
		assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
	}
}
