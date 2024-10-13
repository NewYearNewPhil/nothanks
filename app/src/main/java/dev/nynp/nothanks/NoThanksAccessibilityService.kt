package dev.nynp.nothanks

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class NoThanksAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    private var isBlockingEnabled = false
    private var blockedViews = setOf<String>()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        const val TAG = "NoThanksAccessibility"
    }

    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(applicationContext)

        SettingsManager.isBlockingEnabled
            .onEach { isBlockingEnabled = it }
            .launchIn(serviceScope)

        SettingsManager.blockedViews
            .onEach { views ->
                blockedViews = views.filter { it.isBlocked }.map { it.id }.toSet()
            }
            .launchIn(serviceScope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // dumpViewHierarchy(rootInActiveWindow, 0)

            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                checkForSpecificFeature(rootNode, event)
            }

            broadcastAppChange(event)
        }
    }

    private fun dumpViewHierarchy(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null) return

        val indent = "  ".repeat(depth)
        val nodeInfo = StringBuilder(indent)
        nodeInfo.append("Class: ${node.className}")
        nodeInfo.append(", Text: ${node.text}")
        nodeInfo.append(", ContentDescription: ${node.contentDescription}")
        nodeInfo.append(", ViewIdResourceName: ${node.viewIdResourceName}")

        Log.d(TAG, nodeInfo.toString())

        for (i in 0 until node.childCount) {
            dumpViewHierarchy(node.getChild(i), depth + 1)
        }
    }

    private fun checkForSpecificFeature(rootNode: AccessibilityNodeInfo, event: AccessibilityEvent) {
        if(isBlockingEnabled){
            blockedViews.forEach { id ->
                val detectedView = rootNode.findAccessibilityNodeInfosByViewId(id)
                if(detectedView.isNotEmpty()){
                    Log.d(TAG, "Found Blocked View $id")

                    handler.post({
                        val backSuccess = performGlobalAction(GLOBAL_ACTION_BACK)
                        if (backSuccess) {
                            Log.d(TAG, "Successfully performed back action")
                        } else {
                            Log.d(TAG, "Failed to perform back action")
                        }
                    })
                }
            }
        }
    }

    private fun broadcastAppChange(event: AccessibilityEvent) {
        val intent = Intent("dev.nynp.APP_CHANGED")
        intent.putExtra("package", event.packageName?.toString())
        intent.putExtra("class", event.className?.toString())
        sendBroadcast(intent)
    }

    override fun onInterrupt() {
        Log.d(TAG, "AppMonitorAccessibilityService interrupted")
    }

    override fun onServiceConnected() {
        Log.d(TAG, "AppMonitorAccessibilityService connected")
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
        Log.d(TAG, "Service config: $info")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}