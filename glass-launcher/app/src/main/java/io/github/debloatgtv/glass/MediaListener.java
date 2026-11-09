package io.github.debloatgtv.glass;

import android.service.notification.NotificationListenerService;

/**
 * Only here so Android lets Glass see which app is playing music (media sessions)
 * for the now-playing widget. It does nothing with notifications.
 * Turned on by `gtv glass` (cmd notification allow_listener).
 */
public class MediaListener extends NotificationListenerService {
}
