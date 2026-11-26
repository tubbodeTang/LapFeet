/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.candidates

import android.view.KeyEvent
import org.fcitx.fcitx5.android.core.Key
import org.fcitx.fcitx5.android.data.prefs.HardwareChord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the Mod-Tap (ADR-0005) pure predicates in
 * [HardwareShortcutResolver]: the per-family chord-binding detector (the arm gate's
 * "does this modifier family even have chords" half) and the modifier-family
 * mapping. Prefs-backed wrappers (`isDualRoleBareKey`, `hasChordBindingForModifier`)
 * are exercised on-device; the logic core under test here is
 * [HardwareShortcutResolver.chordBindingForFamily].
 */
class HardwareModTapTest {

    private fun key(sym: Int, states: Int = 0) = Key(sym, states, "", "")

    private fun binding(sym: Int, states: Int = 0): HardwareShortcutResolver.ParsedKey.Ref =
        HardwareShortcutResolver.ParsedKey.Ref(key(sym, states))

    private fun chord(modifier: String, innerSym: Int): HardwareShortcutResolver.ParsedKey.Chord =
        HardwareShortcutResolver.ParsedKey.Chord(modifier, binding(innerSym))

    private val symE = 'e'.code
    private val symQ = 'q'.code

    // ---------------------------------------------------------------- chord-binding gate

    @Test
    fun chordBindingOfFamilyIsConflict() {
        // Alt_R has a chord binding (Alt_R+e) -> Mod-Tap may arm on the Alt_R dual-role key.
        assertTrue(
            HardwareShortcutResolver.chordBindingForFamily(
                HardwareChord.ALT_R,
                listOf(chord(HardwareChord.ALT_R, symE))
            )
        )
    }

    @Test
    fun chordOfDifferentFamilyIsNoConflict() {
        // The dual-role key is Alt_R; a Shift_L+e chord does not make Alt_R arm.
        assertFalse(
            HardwareShortcutResolver.chordBindingForFamily(
                HardwareChord.ALT_R,
                listOf(chord(HardwareChord.SHIFT_L, symE))
            )
        )
    }

    @Test
    fun nonChordActionBindingIsNoConflict() {
        // A plain action binding (no modifier) can never be a chord half.
        assertFalse(
            HardwareShortcutResolver.chordBindingForFamily(
                HardwareChord.ALT_R,
                listOf(binding(symE))
            )
        )
    }

    @Test
    fun pseudoModifierChordsAreTheirOwnFamily() {
        // Sym/Fn chords are governed by the existing tap-hold path, not Mod-Tap:
        // they must NOT arm an Alt/Shift dual-role key.
        assertFalse(
            HardwareShortcutResolver.chordBindingForFamily(
                HardwareChord.ALT_R,
                listOf(chord(HardwareChord.SYM, symE), chord(HardwareChord.FN, symQ))
            )
        )
        assertTrue(
            HardwareShortcutResolver.chordBindingForFamily(
                HardwareChord.SYM,
                listOf(chord(HardwareChord.SYM, symE))
            )
        )
    }

    @Test
    fun emptyBindingsAreNoConflict() {
        assertFalse(HardwareShortcutResolver.chordBindingForFamily(HardwareChord.ALT_R, emptyList()))
    }

    // ---------------------------------------------------------------- family mapping

    @Test
    fun modifierFamilyMapping() {
        assertEquals(
            HardwareChord.ALT_R,
            HardwareShortcutResolver.chordModifierFamilyForKeyCode(KeyEvent.KEYCODE_ALT_RIGHT)
        )
        assertEquals(
            HardwareChord.ALT_L,
            HardwareShortcutResolver.chordModifierFamilyForKeyCode(KeyEvent.KEYCODE_ALT_LEFT)
        )
        assertEquals(
            HardwareChord.SHIFT_L,
            HardwareShortcutResolver.chordModifierFamilyForKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT)
        )
        assertEquals(
            HardwareChord.SHIFT_R,
            HardwareShortcutResolver.chordModifierFamilyForKeyCode(KeyEvent.KEYCODE_SHIFT_RIGHT)
        )
        // Letters and the CapsLock key have no chord family here (pseudo modifiers
        // Fn/Sym are handled by the tap-hold path; CapsLock has no chord role).
        assertNull(HardwareShortcutResolver.chordModifierFamilyForKeyCode(KeyEvent.KEYCODE_E))
        assertNull(HardwareShortcutResolver.chordModifierFamilyForKeyCode(KeyEvent.KEYCODE_CAPS_LOCK))
    }
}
