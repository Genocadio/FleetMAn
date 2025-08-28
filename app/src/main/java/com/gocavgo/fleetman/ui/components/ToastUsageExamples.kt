package com.gocavgo.fleetman.ui.components

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Usage Examples for ToastManager
 * 
 * This file demonstrates how to use the ToastManager in different scenarios:
 * - From Compose UI
 * - From XML layouts
 * - From regular Android functions
 * - From Activities
 */

// ============================================================================
// COMPOSE USAGE EXAMPLES
// ============================================================================

@Composable
fun ComposeToastExamples() {
    val context = LocalContext.current
    val toastManager = rememberToastManager()
    
    // Example 1: Basic success toast
    fun showSuccessToast() {
        context.showSuccessToast("Operation completed successfully!")
    }
    
    // Example 2: Toast with title
    fun showInfoToastWithTitle() {
        context.showInfoToast(
            message = "Your data has been saved",
            title = "Data Saved"
        )
    }
    
    // Example 3: Using ToastManager directly
    fun showCustomToast() {
        ToastManager.show(context, ToastConfig(
            type = ToastType.WARNING,
            title = "Warning",
            message = "This action cannot be undone",
            duration = 6000L,
            action = ToastAction(
                label = "Undo",
                onClick = { /* Handle undo */ }
            )
        ))
    }
    
    // Example 4: Using rememberToastManager in Compose
    fun showToastFromCompose() {
        // You can use the toastManager instance here
        context.showErrorToast("Something went wrong!")
    }
}

// ============================================================================
// XML LAYOUT USAGE EXAMPLES
// ============================================================================

/**
 * Example of how to use ToastManager in XML layouts
 * 
 * In your XML layout, you can call these functions from button clicks
 * or other view interactions.
 */
class XmlLayoutExamples {
    
    fun showToastFromButtonClick(context: Context, rootView: ViewGroup) {
        // Show toast in the specific view group
        rootView.showSuccessToast("Button clicked!")
        
        // Or show toast in the current activity
        context.showInfoToast("Information message")
    }
    
    fun showToastFromActivity(activity: Activity) {
        // Show toast directly in the activity
        activity.showSuccessToast("Success message")
        activity.showErrorToast("Error message", "Error Title")
        activity.showWarningToast("Warning message")
        activity.showInfoToast("Info message")
    }
}

// ============================================================================
// REGULAR FUNCTION USAGE EXAMPLES
// ============================================================================

/**
 * Example of how to use ToastManager from regular functions
 * (non-Compose, non-XML)
 */
class RegularFunctionExamples {
    
    fun showToastFromFunction(context: Context) {
        // Basic usage
        context.showSuccessToast("Function executed successfully")
        
        // With title
        context.showErrorToast(
            message = "Failed to connect to server",
            title = "Connection Error"
        )
        
        // Custom configuration
        context.showToast(ToastConfig(
            type = ToastType.WARNING,
            title = "Custom Warning",
            message = "This is a custom warning message",
            duration = 8000L,
            position = ToastPosition.BOTTOM_CENTER,
            dismissible = false
        ))
    }
    
    fun showToastFromService(context: Context) {
        // Even from services or other contexts
        context.showInfoToast("Service started successfully")
    }
}

// ============================================================================
// ADVANCED USAGE EXAMPLES
// ============================================================================

/**
 * Advanced usage examples with custom configurations
 */
class AdvancedToastExamples {
    
    fun showToastWithAction(context: Context) {
        context.showToast(ToastConfig(
            type = ToastType.INFO,
            title = "New Message",
            message = "You have a new message from John",
            action = ToastAction(
                label = "View",
                onClick = { 
                    // Navigate to message
                    // This will be called when user taps the action button
                }
            )
        ))
    }
    
    fun showToastWithCustomPosition(context: Context) {
        context.showToast(ToastConfig(
            type = ToastType.SUCCESS,
            message = "Custom positioned toast",
            position = ToastPosition.TOP_CENTER,
            duration = 3000L
        ))
    }
    
    fun showNonDismissibleToast(context: Context) {
        context.showToast(ToastConfig(
            type = ToastType.WARNING,
            title = "Important",
            message = "This toast cannot be dismissed manually",
            dismissible = false,
            duration = 10000L
        ))
    }
}

// ============================================================================
// INTEGRATION WITH EXISTING CODE
// ============================================================================

/**
 * Example of how to replace existing toast calls with ToastManager
 */
class IntegrationExamples {
    
    // Before: Using system Toast
    fun oldWay(context: Context) {
        Toast.makeText(context, "Old way", Toast.LENGTH_SHORT).show()
    }
    
    // After: Using ToastManager
    fun newWay(context: Context) {
        context.showInfoToast("New way")
    }
    
    // Before: Using custom toast implementations
    fun oldCustomToast(context: Context) {
        // Old custom toast code
    }
    
    // After: Using ToastManager with custom config
    fun newCustomToast(context: Context) {
        context.showToast(ToastConfig(
            type = ToastType.SUCCESS,
            title = "Custom",
            message = "Custom styled toast",
            duration = 5000L,
            position = ToastPosition.BOTTOM_RIGHT
        ))
    }
}

// ============================================================================
// BEST PRACTICES
// ============================================================================

/**
 * Best practices for using ToastManager
 */
object ToastBestPractices {
    
    /**
     * Use appropriate toast types:
     * - SUCCESS: For successful operations
     * - ERROR: For errors and failures
     * - WARNING: For warnings and cautions
     * - INFO: For general information
     */
    
    /**
     * Keep messages concise and actionable
     */
    fun showGoodToast(context: Context) {
        context.showSuccessToast("Profile updated successfully")
    }
    
    fun showBadToast(context: Context) {
        context.showSuccessToast("The profile has been updated successfully and all changes have been saved to the database")
    }
    
    /**
     * Use titles for important messages
     */
    fun showToastWithTitle(context: Context) {
        context.showErrorToast(
            message = "Please check your internet connection",
            title = "Network Error"
        )
    }
    
    /**
     * Use actions for interactive toasts
     */
    fun showInteractiveToast(context: Context) {
        context.showToast(ToastConfig(
            type = ToastType.INFO,
            title = "File Downloaded",
            message = "Your file has been downloaded successfully",
            action = ToastAction(
                label = "Open",
                onClick = { /* Open file */ }
            )
        ))
    }
}
