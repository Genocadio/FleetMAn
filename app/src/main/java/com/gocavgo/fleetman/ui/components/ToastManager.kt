package com.gocavgo.fleetman.ui.components

import android.app.Activity
import android.content.Context
import com.gocavgo.fleetman.ui.components.ActivityTracker
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Toast types with their respective colors and icons
 */
enum class ToastType(val color: Color, val icon: ImageVector, val backgroundColor: Color) {
    SUCCESS(
        color = Color(0xFF1B5E20),
        icon = Icons.Default.CheckCircle,
        backgroundColor = Color(0xFFE8F5E8)
    ),
    ERROR(
        color = Color(0xFFD32F2F),
        icon = Icons.Default.Error,
        backgroundColor = Color(0xFFFFEBEE)
    ),
    WARNING(
        color = Color(0xFFF57C00),
        icon = Icons.Default.Warning,
        backgroundColor = Color(0xFFFFF3E0)
    ),
    INFO(
        color = Color(0xFF1976D2),
        icon = Icons.Default.Info,
        backgroundColor = Color(0xFFE3F2FD)
    )
}

/**
 * Toast configuration options
 */
data class ToastConfig(
    val type: ToastType = ToastType.INFO,
    val title: String? = null,
    val message: String,
    val duration: Long = 4000L,
    val position: ToastPosition = ToastPosition.TOP_RIGHT,
    val dismissible: Boolean = true,
    val action: ToastAction? = null,
    val onClick: (() -> Unit)? = null // Click handler for entire toast
)

/**
 * Toast action configuration
 */
data class ToastAction(
    val label: String,
    val onClick: () -> Unit
)

/**
 * Toast position options
 */
enum class ToastPosition(val gravity: Int, val offsetX: Int, val offsetY: Int) {
    TOP_LEFT(Gravity.TOP or Gravity.START, 16, 100),
    TOP_CENTER(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 100),
    TOP_RIGHT(Gravity.TOP or Gravity.END, -16, 100),
    BOTTOM_LEFT(Gravity.BOTTOM or Gravity.START, 16, -100),
    BOTTOM_CENTER(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, -100),
    BOTTOM_RIGHT(Gravity.BOTTOM or Gravity.END, -16, -100)
}

/**
 * Main Toast Manager class that handles both Compose and XML toast displays
 */
object ToastManager {
    private val activeToasts = mutableListOf<ToastView>()
    private const val MAX_TOASTS = 5

    /**
     * Show a toast from anywhere in the app (including non-Compose functions)
     */
    fun show(context: Context, config: ToastConfig) {
        // First try to get tracked current activity
        var activity = ActivityTracker.getCurrentActivity()
        
        // If not found, try to find from context
        if (activity == null) {
            activity = findCurrentActivity(context)
        }
        
        if (activity != null) {
            showInActivity(activity, config)
        } else {
            // Fallback to system toast
            showSystemToast(context, config)
        }
    }

    /**
     * Show a toast in a specific activity
     */
    fun showInActivity(activity: Activity, config: ToastConfig) {
        val rootView = findRootView(activity)
        if (rootView != null) {
            showInView(rootView, config)
        } else {
            showSystemToast(activity, config)
        }
    }

    /**
     * Show a toast in a specific view group (for XML layouts)
     */
    fun showInView(viewGroup: ViewGroup, config: ToastConfig) {
        val toastView = ToastView(viewGroup.context, config)
        activeToasts.add(toastView)
        
        // Remove oldest toast if we exceed max
        if (activeToasts.size > MAX_TOASTS) {
            activeToasts.removeAt(0).dismiss()
        }
        
        // Add to the view group
        viewGroup.addView(toastView)
        
        // Position the toast
        positionToastInView(toastView, viewGroup)
        
        // Auto-dismiss after duration
        toastView.postDelayed({
            dismiss(toastView)
        }, config.duration)
    }

    /**
     * Convenience methods for different toast types
     */
    fun success(context: Context, message: String, title: String? = null) {
        show(context, ToastConfig(
            type = ToastType.SUCCESS,
            title = title,
            message = message
        ))
    }

    fun error(context: Context, message: String, title: String? = null) {
        show(context, ToastConfig(
            type = ToastType.ERROR,
            title = title,
            message = message
        ))
    }

    fun warning(context: Context, message: String, title: String? = null) {
        show(context, ToastConfig(
            type = ToastType.WARNING,
            title = title,
            message = message
        ))
    }

    fun info(context: Context, message: String, title: String? = null) {
        show(context, ToastConfig(
            type = ToastType.INFO,
            title = title,
            message = message
        ))
    }

    /**
     * Dismiss a specific toast
     */
    private fun dismiss(toastView: ToastView) {
        if (activeToasts.contains(toastView)) {
            activeToasts.remove(toastView)
            toastView.dismiss()
        }
    }

    /**
     * Dismiss all active toasts
     */
    fun dismissAll() {
        activeToasts.forEach { it.dismiss() }
        activeToasts.clear()
    }

    /**
     * Find the current activity from context
     */
    private fun findCurrentActivity(context: Context): Activity? {
        var currentContext = context
        while (currentContext is Context) {
            if (currentContext is Activity) {
                return currentContext
            }
            currentContext = currentContext.applicationContext
        }
        return null
    }

    /**
     * Find the root view of an activity
     */
    private fun findRootView(activity: Activity): ViewGroup? {
        return try {
            val decorView = activity.window.decorView
            val contentView = decorView.findViewById<ViewGroup>(android.R.id.content)
            contentView ?: decorView as? ViewGroup
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Position the toast within a view group
     */
    private fun positionToastInView(toastView: ToastView, viewGroup: ViewGroup) {
        val config = toastView.config
        
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = config.position.gravity
            setMargins(
                config.position.offsetX,
                config.position.offsetY,
                -config.position.offsetX,
                -config.position.offsetY
            )
        }
        
        toastView.layoutParams = params
        toastView.animateIn()
    }

    /**
     * Fallback to system toast
     */
    private fun showSystemToast(context: Context, config: ToastConfig) {
        val message = if (config.title != null) "${config.title}: ${config.message}" else config.message
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

/**
 * Individual toast view that handles the visual representation
 */
private class ToastView(
    context: Context,
    val config: ToastConfig
) : FrameLayout(context) {
    
    private var isDismissed = false

    init {
        setupView()
    }

    private fun setupView() {
        // Create Compose view for the toast content
        val composeView = ComposeView(context).apply {
            setContent {
                ToastContent(
                    config = config,
                    onDismiss = { dismiss() }
                )
            }
        }

        addView(composeView)
        
        // Add click listener - onClick takes precedence over dismiss
        setOnClickListener {
            if (config.onClick != null) {
                config.onClick?.invoke()
            } else if (config.dismissible) {
                dismiss()
            }
        }
    }

    fun animateIn() {
        alpha = 0f
        translationY = -100f
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .start()
    }

    fun dismiss() {
        if (isDismissed) return
        isDismissed = true
        
        animate()
            .alpha(0f)
            .translationY(-100f)
            .setDuration(300)
            .withEndAction {
                try {
                    (parent as? ViewGroup)?.removeView(this)
                } catch (e: Exception) {
                    // View already removed
                }
            }
            .start()
    }
}

/**
 * Compose content for the toast
 */
@Composable
private fun ToastContent(
    config: ToastConfig,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .widthIn(max = 400.dp)
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = config.type.backgroundColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Icon(
                imageVector = config.type.icon,
                contentDescription = null,
                tint = config.type.color,
                modifier = Modifier.size(24.dp)
            )
            
            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                config.title?.let { title ->
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = config.type.color
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                
                Text(
                    text = config.message,
                    fontSize = 13.sp,
                    color = config.type.color.copy(alpha = 0.8f)
                )
                
                // Action button if provided
                config.action?.let { action ->
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = action.onClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = config.type.color
                        ),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = action.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Dismiss button
            if (config.dismissible) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = config.type.color.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compose-specific toast functions for use within Compose UI
 */
@Composable
fun rememberToastManager(): ToastManager {
    return remember { ToastManager }
}

/**
 * Extension functions for easier toast usage in Compose
 */
fun Context.showToast(config: ToastConfig) = ToastManager.show(this, config)
fun Context.showSuccessToast(message: String, title: String? = null) = ToastManager.success(this, message, title)
fun Context.showErrorToast(message: String, title: String? = null) = ToastManager.error(this, message, title)
fun Context.showWarningToast(message: String, title: String? = null) = ToastManager.warning(this, message, title)
fun Context.showInfoToast(message: String, title: String? = null) = ToastManager.info(this, message, title)

/**
 * Extension functions for showing toasts in view groups
 */
fun ViewGroup.showToast(config: ToastConfig) = ToastManager.showInView(this, config)
fun ViewGroup.showSuccessToast(message: String, title: String? = null) = ToastManager.showInView(this, ToastConfig(type = ToastType.SUCCESS, title = title, message = message))
fun ViewGroup.showErrorToast(message: String, title: String? = null) = ToastManager.showInView(this, ToastConfig(type = ToastType.ERROR, title = title, message = message))
fun ViewGroup.showWarningToast(message: String, title: String? = null) = ToastManager.showInView(this, ToastConfig(type = ToastType.WARNING, title = title, message = message))
fun ViewGroup.showInfoToast(message: String, title: String? = null) = ToastManager.showInView(this, ToastConfig(type = ToastType.INFO, title = title, message = message))

/**
 * Extension functions for activities
 */
fun Activity.showToast(config: ToastConfig) = ToastManager.showInActivity(this, config)
fun Activity.showSuccessToast(message: String, title: String? = null) = ToastManager.showInActivity(this, ToastConfig(type = ToastType.SUCCESS, title = title, message = message))
fun Activity.showErrorToast(message: String, title: String? = null) = ToastManager.showInActivity(this, ToastConfig(type = ToastType.ERROR, title = title, message = message))
fun Activity.showWarningToast(message: String, title: String? = null) = ToastManager.showInActivity(this, ToastConfig(type = ToastType.WARNING, title = title, message = message))
fun Activity.showInfoToast(message: String, title: String? = null) = ToastManager.showInActivity(this, ToastConfig(type = ToastType.INFO, title = title, message = message))
