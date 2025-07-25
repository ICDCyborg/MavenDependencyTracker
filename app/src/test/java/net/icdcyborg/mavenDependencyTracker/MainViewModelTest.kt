package net.icdcyborg.mavenDependencyTracker

import net.icdcyborg.mavenDependencyTracker.domain.DependencyRepository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class MainViewModelTest {

    private lateinit var viewModel: MainViewModel
    private lateinit var dependencyRepository: DependencyRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dependencyRepository = mockk()
        viewModel = MainViewModel(dependencyRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startResolution with valid coordinate should update uiState correctly`() = runTest {
        val coordinate = "a:b:c"
        val dependencies = listOf("dep1", "dep2")
        coEvery { dependencyRepository.resolveDependencies(coordinate) } returns flowOf(*dependencies.toTypedArray())

        viewModel.uiState.test {
            viewModel.onMavenCoordinateInputChange(coordinate)
            viewModel.startResolution()

            assertThat(awaitItem().isResolving).isFalse()
            assertThat(awaitItem().isResolving).isTrue()

            dependencies.forEach {
                assertThat(awaitItem().resolvedDependencies).contains(it)
            }

            assertThat(awaitItem().isResolving).isFalse()
        }
    }

    @Test
    fun `startResolution with invalid coordinate should set error`() = runTest {
        val invalidCoordinate = "invalid"

        viewModel.uiState.test {
            viewModel.onMavenCoordinateInputChange(invalidCoordinate)
            viewModel.startResolution()
            skipItems(1)
            val state = awaitItem()
            assertThat(state.error).isEqualTo("入力形式が正しくありません (例: group:artifact:version)")
        }
    }
}
