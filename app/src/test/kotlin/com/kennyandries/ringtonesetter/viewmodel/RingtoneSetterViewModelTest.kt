package com.kennyandries.ringtonesetter.viewmodel

import android.net.Uri
import com.kennyandries.ringtonesetter.config.ManagedConfig
import com.kennyandries.ringtonesetter.config.ManagedConfigReader
import com.kennyandries.ringtonesetter.contacts.ContactRingtoneAssigner
import com.kennyandries.ringtonesetter.download.RingtoneDownloader
import com.kennyandries.ringtonesetter.ringtone.RingtoneRegistrar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class RingtoneSetterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val configReader: ManagedConfigReader = mock()
    private val downloader: RingtoneDownloader = mock()
    private val registrar: RingtoneRegistrar = mock()
    private val assigner: ContactRingtoneAssigner = mock()

    private lateinit var viewModel: RingtoneSetterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = RingtoneSetterViewModel(
            configReader = configReader,
            downloader = downloader,
            registrar = registrar,
            assigner = assigner,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refreshConfig sets valid status`() {
        val config = ManagedConfig(
            ringtoneSasUrl = "https://example.com/ringtone.mp3",
            contactPhoneNumbers = listOf("+14155552671", "+12125551234"),
            ringtoneDisplayName = "Test Ringtone",
        )
        whenever(configReader.read()).thenReturn(ManagedConfig.Result.Valid(config))

        viewModel.refreshConfig()

        val state = viewModel.uiState.value
        val configStatus = state.configStatus as ConfigStatus.Valid
        assertEquals("Test Ringtone", configStatus.displayName)
        assertEquals(2, configStatus.phoneNumberCount)
        assertEquals(null, state.error)
        assertEquals(null, state.results)
    }

    @Test
    fun `applyRingtone sets invalid config state when config invalid`() {
        val errors = listOf("Ringtone SAS URL is not configured")
        whenever(configReader.read()).thenReturn(ManagedConfig.Result.Invalid(errors))

        viewModel.applyRingtone()

        val state = viewModel.uiState.value
        val configStatus = state.configStatus as ConfigStatus.Invalid
        assertEquals(errors, configStatus.errors)
        assertEquals("Configuration is invalid", state.error)
        verify(downloader, never()).download(any(), any())
    }

    @Test
    fun `applyRingtone completes and sets done state on success`() = runTest {
        val config = ManagedConfig(
            ringtoneSasUrl = "https://example.com/ringtone.ogg",
            contactPhoneNumbers = listOf("+14155552671"),
            ringtoneDisplayName = "Ops Ringtone",
        )
        val ringtoneUri: Uri = mock()
        val prepared = RingtoneRegistrar.PreparedRingtone(ringtoneUri, ByteArrayOutputStream())
        val expectedResults = listOf(
            ContactRingtoneAssigner.AssignmentResult(
                phoneNumber = "+14155552671",
                success = true,
                contactName = "Help Desk",
            )
        )

        whenever(configReader.read()).thenReturn(ManagedConfig.Result.Valid(config))
        whenever(registrar.prepare(eq("Ops Ringtone"), eq("audio/mpeg"))).thenReturn(prepared)
        whenever(downloader.download(eq("https://example.com/ringtone.ogg"), any()))
            .thenReturn(RingtoneDownloader.DownloadResult("audio/ogg", 1024))
        whenever(assigner.assign(eq(listOf("+14155552671")), eq(ringtoneUri))).thenReturn(expectedResults)

        viewModel.applyRingtone()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(OperationPhase.Done, state.operationPhase)
        assertEquals(expectedResults, state.results)
        assertEquals(null, state.error)
        inOrder(registrar) {
            verify(registrar).finalize(ringtoneUri)
            verify(registrar).updateMimeType(ringtoneUri, "audio/ogg")
        }
        verify(registrar, never()).cleanup(any())
    }

    @Test
    fun `applyRingtone returns to idle and reports error when download fails`() = runTest {
        val config = ManagedConfig(
            ringtoneSasUrl = "https://example.com/ringtone.mp3",
            contactPhoneNumbers = listOf("+14155552671"),
            ringtoneDisplayName = "Ops Ringtone",
        )
        val ringtoneUri: Uri = mock()
        val prepared = RingtoneRegistrar.PreparedRingtone(ringtoneUri, ByteArrayOutputStream())

        whenever(configReader.read()).thenReturn(ManagedConfig.Result.Valid(config))
        whenever(registrar.prepare(eq("Ops Ringtone"), eq("audio/mpeg"))).thenReturn(prepared)
        whenever(downloader.download(eq("https://example.com/ringtone.mp3"), any()))
            .thenThrow(RuntimeException("Download failed: HTTP 403"))

        viewModel.applyRingtone()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(OperationPhase.Idle, state.operationPhase)
        assertTrue(state.error?.contains("HTTP 403") == true)
        verify(registrar).cleanup(ringtoneUri)
        verify(assigner, never()).assign(any(), any())
    }
}
