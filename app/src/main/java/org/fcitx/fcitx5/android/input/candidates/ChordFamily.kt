/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.candidates

import android.view.KeyEvent
import org.fcitx.fcitx5.android.data.prefs.HardwareChord
import org.fcitx.fcitx5.android.input.candidates.HardwareShortcutResolver.ParsedKey

/**
 * JVM-pure Mod-Tap (ADR-0005) chord-family helpers.
 *
 * Kept in its own object instead of inside [HardwareShortcutResolver] so that unit tests
 * can reach this logic without triggering the resolver object's `<clinit>` — the resolver
 * binds `AppPrefs` (Android-only) the first time it is touched, which is fatal on the JVM.
 * Referencing the nested [ParsedKey] type below does NOT initialize the resolver.
 */
internal object ChordFamily {

    /** 物理修饰键 keyCode → 和弦修饰键家族名（[HardwareChord] const）。非修饰键返回 null。 */
    fun modifierFamilyForKeyCode(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_ALT_LEFT -> HardwareChord.ALT_L
        KeyEvent.KEYCODE_ALT_RIGHT -> HardwareChord.ALT_R
        KeyEvent.KEYCODE_SHIFT_LEFT -> HardwareChord.SHIFT_L
        KeyEvent.KEYCODE_SHIFT_RIGHT -> HardwareChord.SHIFT_R
        else -> null
    }

    /**
     * 纯函数核心（单测直达）：[actionBindings] 里是否存在 [family] 家族的和弦绑定
     * （`family+字母` 的动作快捷键）。存在时该家族的双角色键裸按才会挂起（Mod-Tap）；
     * 不存在则永不挂起，行为与旧版逐字节相同（零面变更）。
     */
    fun bindingForFamily(family: String, actionBindings: List<ParsedKey>): Boolean =
        actionBindings.any { (it as? ParsedKey.Chord)?.modifier == family }
}
