package com.example

import com.example.core.model.AppResult
import com.example.core.model.DomainError
import com.example.core.storage.LocalFileStorageProvider
import com.example.core.storage.WorkspaceSeeder
import com.example.engines.apk.ApkLabEngine
import com.example.engines.archive.ZipArchiveEngine
import com.example.engines.editor.BoundedUndoRedoStack
import com.example.engines.editor.DiffEngine
import com.example.engines.editor.HexEngine
import com.example.engines.editor.TextEditorEngine
import com.example.engines.system.ApkFunctionAction
import com.example.engines.system.ApkProFunctionsEngine
import com.example.engines.system.HonestCapabilityRegistry
import com.example.engines.system.LocalLanTransferServer
import com.example.engines.system.LocalTerminalSimulator
import com.example.engines.system.OptimizeApkOptions
import com.example.engines.system.SigningKeyManagerEngine
import com.example.engines.system.SmaliInstructionReference
import com.example.engines.system.VirtualArchiveBrowserEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExampleUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `file operations preserve source and enforce overwrite protection`() {
        val trashDir = tempFolder.newFolder("trash")
        val provider = LocalFileStorageProvider(trashDir)
        val paneA = tempFolder.newFolder("paneA")
        val paneB = tempFolder.newFolder("paneB")

        val createRes = provider.createNewFile(paneA.absolutePath, "hello.txt", "YemenManagerTest".toByteArray())
        assertTrue(createRes is AppResult.Success)
        val sourceFile = File(paneA, "hello.txt")

        // First copy succeeds
        val copy1 = provider.copyStreaming(sourceFile.absolutePath, paneB.absolutePath, overwrite = false)
        assertTrue(copy1 is AppResult.Success)
        assertTrue(sourceFile.exists()) // FR-FM-009: source never deleted on copy

        // Second copy without overwrite fails with DestinationExists
        val copy2 = provider.copyStreaming(sourceFile.absolutePath, paneB.absolutePath, overwrite = false)
        assertTrue(copy2 is AppResult.Failure)
        assertTrue((copy2 as AppResult.Failure).error is DomainError.DestinationExists)

        // Hash computation
        val hashRes = provider.computeHashes(sourceFile.absolutePath)
        assertTrue(hashRes is AppResult.Success)
        val hashes = (hashRes as AppResult.Success).data
        assertEquals(32, hashes.md5.length)
        assertEquals(40, hashes.sha1.length)
        assertEquals(64, hashes.sha256.length)
    }

    @Test
    fun `text editor undo redo bounds and atomic working copy save`() {
        val trashDir = tempFolder.newFolder("trash")
        val provider = LocalFileStorageProvider(trashDir)
        val dir = tempFolder.newFolder("editor_test")
        val src = File(dir, "config.json").apply { writeText("{\"v\":1}") }

        val stack = BoundedUndoRedoStack(maxSteps = 3)
        stack.pushState("{\"v\":1}")
        stack.pushState("{\"v\":2}")
        stack.pushState("{\"v\":3}")
        stack.pushState("{\"v\":4}")
        assertEquals(3, stack.undoCount) // Strictly bounded to maxSteps

        val engine = TextEditorEngine()
        val replaced = engine.findAndReplace("{\"v\":1}", "1", "2", useRegex = false)
        assertTrue(replaced is AppResult.Success)
        assertEquals("{\"v\":2}", (replaced as AppResult.Success).data.first)

        // Write working copy without touching original
        val saveWork = provider.writeAtomicWorkingCopy(src.absolutePath, "{\"v\":2}".toByteArray(), replaceOriginal = false)
        assertTrue(saveWork is AppResult.Success)
        assertEquals("{\"v\":1}", src.readText()) // Original preserved!
        assertTrue(File(dir, "config.json.ym_work").exists())
    }

    @Test
    fun `hex engine reads windowed pages searches bytes and commits working copy`() {
        val dir = tempFolder.newFolder("hex_test")
        val binFile = File(dir, "sample.bin")
        val data = ByteArray(600) { (it and 0xFF).toByte() }
        data[10] = 0x59 // 'Y'
        data[11] = 0x4D // 'M'
        binFile.writeBytes(data)

        val hexEngine = HexEngine(pageSize = 256)
        val winRes = hexEngine.readWindow(binFile.absolutePath, pageOffset = 0L)
        assertTrue(winRes is AppResult.Success)
        val window = (winRes as AppResult.Success).data
        assertEquals(16, window.rows.size) // 256 / 16 = 16 rows

        val searchRes = hexEngine.searchBytes(binFile.absolutePath, "59 4D", isHexPattern = true)
        assertTrue(searchRes is AppResult.Success)
        assertTrue((searchRes as AppResult.Success).data.contains(10L))

        val commitRes = hexEngine.commitPatchesSafely(
            sourcePath = binFile.absolutePath,
            patches = mapOf(0L to 0x7F.toByte()),
            replaceOriginal = false
        )
        assertTrue(commitRes is AppResult.Success)
        assertEquals(0.toByte(), binFile.readBytes()[0]) // Original unchanged
        assertEquals(0x7F.toByte(), File((commitRes as AppResult.Success).data).readBytes()[0])
    }

    @Test
    fun `diff engine computes line and binary differences`() {
        val dir = tempFolder.newFolder("diff_test")
        val f1 = File(dir, "a.txt").apply { writeText("line1\nline2\nline3") }
        val f2 = File(dir, "b.txt").apply { writeText("line1\nline2_mod\nline3\nline4") }

        val diffEngine = DiffEngine()
        val res = diffEngine.compareFiles(f1.absolutePath, f2.absolutePath)
        assertTrue(res is AppResult.Success)
        val report = (res as AppResult.Success).data
        assertTrue(report.isTextComparison)
        assertTrue(report.addedCount >= 2)
        assertTrue(report.removedCount >= 1)
        assertTrue(report.binarySummary.differingBytesCount > 0)
    }

    @Test
    fun `zip archive engine blocks zip slip traversal and extracts valid archives`() {
        val dir = tempFolder.newFolder("zip_test")
        val zipEngine = ZipArchiveEngine()

        assertTrue(zipEngine.isUnsafeEntryPath("../evil.sh"))
        assertTrue(zipEngine.isUnsafeEntryPath("foo/../../etc/passwd"))
        assertTrue(zipEngine.isUnsafeEntryPath("/abs/path.txt"))
        assertFalse(zipEngine.isUnsafeEntryPath("docs/readme.txt"))

        // Create a malicious ZIP containing "../escaped.txt"
        val maliciousZip = File(dir, "malicious.zip")
        ZipOutputStream(FileOutputStream(maliciousZip)).use { zos ->
            zos.putNextEntry(ZipEntry("../escaped.txt"))
            zos.write("pwned".toByteArray())
            zos.closeEntry()
        }
        val extractOut = File(dir, "out_malicious")
        val extractMalicious = zipEngine.extractEntries(maliciousZip.absolutePath, extractOut.absolutePath)
        assertTrue(extractMalicious is AppResult.Failure)
        assertTrue((extractMalicious as AppResult.Failure).error is DomainError.ValidationFailed)
        assertFalse(File(dir, "escaped.txt").exists())

        // Create and extract a clean ZIP
        val srcFile = File(dir, "clean.txt").apply { writeText("Safe archive content") }
        val cleanZip = File(dir, "clean.zip")
        val createRes = zipEngine.createZipArchive(listOf(srcFile.absolutePath), cleanZip.absolutePath)
        assertTrue(createRes is AppResult.Success)

        val cleanOut = File(dir, "out_clean")
        val extractClean = zipEngine.extractEntries(cleanZip.absolutePath, cleanOut.absolutePath)
        assertTrue(extractClean is AppResult.Success)
        assertEquals("Safe archive content", File(cleanOut, "clean.txt").readText())
    }

    @Test
    fun `apk lab golden test analyzes axml arsc dex and rebuilds signed working apk`() {
        val dir = tempFolder.newFolder("apk_golden")
        val sampleApk = File(dir, "golden_sample.apk")
        WorkspaceSeeder.createSampleApk(
            destApk = sampleApk,
            packageName = "com.yemenmanager.golden",
            versionName = "2.1.0",
            versionCode = 21
        )

        val apkEngine = ApkLabEngine()
        val analysis = apkEngine.analyzeApk(sampleApk.absolutePath)
        assertTrue(analysis is AppResult.Success)
        val report = (analysis as AppResult.Success).data

        assertEquals("com.yemenmanager.golden", report.manifestInfo.packageName)
        assertEquals("2.1.0", report.manifestInfo.versionName)
        assertEquals(21, report.manifestInfo.versionCode)
        assertTrue(report.arscSummary.isPresent)
        assertEquals(1, report.dexReports.size)
        val dex = report.dexReports.first()
        assertTrue(dex.checksumVerified)
        assertTrue(dex.sampleClasses.contains("Lcom/yemenmanager/sample/MainActivity;"))
        assertTrue(dex.sampleMethods.any { it.contains("onCreate") })

        // Rebuild and sign working copy
        val rebuiltRes = apkEngine.rebuildAndSignWorkingApk(
            sourceApkPath = sampleApk.absolutePath,
            newVersionName = "2.2.0-signed",
            newVersionCode = 22,
            customAssetNote = "Golden Test Rebuild"
        )
        assertTrue(rebuiltRes is AppResult.Success)
        val rebuiltReport = (rebuiltRes as AppResult.Success).data
        assertEquals("2.2.0-signed", rebuiltReport.manifestInfo.versionName)
        assertEquals(22, rebuiltReport.manifestInfo.versionCode)
        assertTrue(rebuiltReport.signatureReport.hasV1JarSignature)
        assertTrue(rebuiltReport.signatureReport.signatureEntries.contains("META-INF/CERT.RSA"))
    }

    @Test
    fun `virtual archive browser apk pro functions terminal simulator and smali reference work offline`() {
        val dir = tempFolder.newFolder("pro_toolkit")
        val sampleApk = File(dir, "pro_test.apk")
        WorkspaceSeeder.createSampleApk(
            destApk = sampleApk,
            packageName = "com.yemenmanager.pro",
            versionName = "1.0.0",
            versionCode = 1
        )

        // 1. Virtual Archive Browser inside pane
        val vRoot = VirtualArchiveBrowserEngine.buildVirtualPath(sampleApk.absolutePath, "")
        val listRoot = VirtualArchiveBrowserEngine.listVirtualDirectory(vRoot)
        assertTrue(listRoot is AppResult.Success)
        val rootEntries = (listRoot as AppResult.Success).data
        assertTrue(rootEntries.any { it.name == "AndroidManifest.xml" })
        assertTrue(rootEntries.any { it.name == "classes.dex" })
        assertTrue(rootEntries.any { it.name == "assets" && it.isDirectory })

        // 2. APK Pro Functions (Optimize APK & Translation extraction)
        val apkLab = ApkLabEngine()
        val proEngine = ApkProFunctionsEngine(apkLab)
        val optRes = proEngine.optimizeApk(sampleApk.absolutePath, OptimizeApkOptions(performancePriority = true, autoSign = true))
        assertTrue(optRes is AppResult.Success)
        assertTrue((optRes as AppResult.Success).data.exists())

        val report = (apkLab.analyzeApk(sampleApk.absolutePath) as AppResult.Success).data
        val transRes = proEngine.executeApkFunction(ApkFunctionAction.XML_TRANSLATION_MODE, report)
        assertTrue(transRes is AppResult.Success)

        // 3. Signing Key Manager
        val keysDir = File(dir, "keys")
        val keyMgr = SigningKeyManagerEngine(keysDir)
        val keys = keyMgr.listOrSeedKeys()
        assertTrue(keys.isNotEmpty())

        // 4. Terminal Simulator & Smali Reference
        val (_, lsOut) = LocalTerminalSimulator.executeCommand("ls", dir)
        assertTrue(lsOut.contains("pro_test.apk"))
        assertTrue(SmaliInstructionReference.opcodes.size >= 25)
    }

    @Test
    fun `lan server is stopped by default requires token and extension registry blocks root`() {
        val dir = tempFolder.newFolder("lan_test")
        File(dir, "shared.txt").writeText("LAN test file")

        val server = LocalLanTransferServer()
        assertFalse(server.getState().isRunning) // FR-NET-003: Stopped by default

        val startRes = server.startServer(dir)
        assertTrue(startRes is AppResult.Success)
        val state = (startRes as AppResult.Success).data
        assertTrue(state.isRunning)
        assertNotNull(state.sessionToken)

        // Anonymous request without token must return 401
        val unauthConn = URL("http://${state.bindAddress}:${state.port}/").openConnection() as HttpURLConnection
        assertEquals(401, unauthConn.responseCode)
        unauthConn.disconnect()

        // Authenticated request with token returns 200
        val authConn = URL("http://${state.bindAddress}:${state.port}/?token=${state.sessionToken}").openConnection() as HttpURLConnection
        assertEquals(200, authConn.responseCode)
        authConn.disconnect()

        val stopped = server.stopServer()
        assertFalse(stopped.isRunning)

        // Extension validator rejects root requests (FR-EXT-004)
        val badExt = HonestCapabilityRegistry.validateExtensionManifestJson(
            """{"id":"bad.ext","name":"RootHack","version":"1.0","permissions":["root"]}"""
        )
        assertTrue(badExt is AppResult.Failure)

        val goodExt = HonestCapabilityRegistry.validateExtensionManifestJson(
            """{"id":"good.ext","name":"SafePlugin","version":"1.0","permissions":["READ_WORKSPACE"]}"""
        )
        assertTrue(goodExt is AppResult.Success)
    }
}
