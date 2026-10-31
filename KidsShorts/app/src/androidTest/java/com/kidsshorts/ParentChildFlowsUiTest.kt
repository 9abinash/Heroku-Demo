package com.kidsshorts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kidsshorts.di.AppContainer
import com.kidsshorts.ui.child.ChildFeedScreen
import com.kidsshorts.ui.child.EMPTY_FEED_MESSAGE
import com.kidsshorts.ui.onboarding.OnboardingScreen
import com.kidsshorts.ui.theme.KidsShortsTheme
import com.kidsshorts.ui.viewmodel.AuthViewModel
import com.kidsshorts.ui.viewmodel.ChildViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented UI tests. They start from a clean slate (all app data deleted). */
@RunWith(AndroidJUnit4::class)
class ParentChildFlowsUiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var container: AppContainer

    @Before fun cleanSlate() {
        container = AppContainer(ApplicationProvider.getApplicationContext())
        runBlocking { container.resetAllData() }
    }

    @Test fun child_withNoApprovedChannels_seesTheExactEmptyMessage_andAParentExit() {
        val vm = ChildViewModel(container)
        rule.setContent { KidsShortsTheme { ChildFeedScreen(vm, container.applicationId) {} } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText(EMPTY_FEED_MESSAGE).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Parent area").assertExists()
    }

    @Test fun onboarding_rejectsMismatchedPins_andCreatesNoCredential() {
        val auth = AuthViewModel(container)
        var finished = false
        rule.setContent { KidsShortsTheme { OnboardingScreen(auth) { finished = true } } }

        rule.onNodeWithText("Create parent PIN").performClick()
        rule.onNodeWithText("PIN").performTextInput("4821")
        rule.onNodeWithText("Confirm PIN").performTextInput("4822")
        rule.onNodeWithText("Save PIN").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("The two PINs don't match.").fetchSemanticsNodes().isNotEmpty() }

        // The mismatch guard fired, so no credential may exist yet.
        assertTrue(!container.pinManager.isPinSet())
        assertTrue(!finished)
    }
}
