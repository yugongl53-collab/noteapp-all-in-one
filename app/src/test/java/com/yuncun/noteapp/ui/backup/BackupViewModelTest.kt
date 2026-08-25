package com.yuncun.noteapp.ui.backup

import com.yuncun.noteapp.data.backup.BackupImportResult
import com.yuncun.noteapp.data.backup.BackupOperations
import com.yuncun.noteapp.data.backup.BackupSnapshot
import com.yuncun.noteapp.domain.model.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 验证文件选择、校验确认和失败反馈不会跳过用户确认。 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun export_requiresWarningThenWritesGeneratedJson() = runTest(dispatcher) {
        val operations = FakeOperations()
        val viewModel = BackupViewModel(operations)
        var chosen = false
        var written = ""

        viewModel.requestExport()
        assertTrue(viewModel.uiState.value.showExportWarning)
        viewModel.confirmExportWarning { chosen = true }
        viewModel.export("backup.json") { written = it }
        advanceUntilIdle()

        assertTrue(chosen)
        assertEquals("{}", written)
        assertEquals("已导出到 backup.json", viewModel.uiState.value.feedback)
    }

    @Test
    fun preparedImport_waitsForConfirmationAndRefreshesAfterSuccess() = runTest(dispatcher) {
        val operations = FakeOperations()
        val viewModel = BackupViewModel(operations)
        var refreshed = false

        viewModel.prepareImport("backup.json") { "{}" }
        advanceUntilIdle()
        assertEquals("backup.json", viewModel.uiState.value.pendingImport?.fileName)
        assertFalse(operations.imported)

        viewModel.confirmImport { refreshed = true }
        advanceUntilIdle()

        assertTrue(operations.imported)
        assertTrue(refreshed)
        assertNull(viewModel.uiState.value.pendingImport)
    }

    @Test
    fun invalidFile_showsFailureWithoutPendingConfirmation() = runTest(dispatcher) {
        val operations = FakeOperations(prepareFailure = IllegalArgumentException("未知版本"))
        val viewModel = BackupViewModel(operations)

        viewModel.prepareImport("bad.json") { "{}" }
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingImport)
        assertTrue(viewModel.uiState.value.feedback?.contains("未知版本") == true)
    }

    private class FakeOperations(
        private val prepareFailure: Throwable? = null
    ) : BackupOperations {
        var imported = false

        override suspend fun exportJson(): String = "{}"

        override suspend fun prepareImport(content: String): BackupSnapshot {
            prepareFailure?.let { throw it }
            return EMPTY_SNAPSHOT
        }

        override suspend fun import(snapshot: BackupSnapshot): BackupImportResult {
            imported = true
            return BackupImportResult(0, null)
        }
    }

    private companion object {
        val EMPTY_SNAPSHOT = BackupSnapshot(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), AppSettings()
        )
    }
}
