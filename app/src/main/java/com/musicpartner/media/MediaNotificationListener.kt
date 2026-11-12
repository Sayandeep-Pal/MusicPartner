package com.musicpartner.media

import android.service.notification.NotificationListenerService

/**
 * Intentionally empty. Android only lets an app read and control other apps' media sessions
 * (MediaSessionManager.getActiveSessions) if the user has granted it "notification access",
 * which is attached to a NotificationListenerService. We never read notifications.
 */
class MediaNotificationListener : NotificationListenerService()
