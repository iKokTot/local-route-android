package org.localroute.android.localdpi

import org.localroute.android.shortcuts.ModeTileService
import org.localroute.android.shortcuts.ShortcutMode

/** Keeps the installed DPI tile's component identity across updates. */
class LocalRouteTileService : ModeTileService() { override val mode = ShortcutMode.DPI }
