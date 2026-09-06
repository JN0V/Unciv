package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.ImageGetter

/** Widgets shared by the phone (portrait) screens */
object PortraitWidgets {
    val buttonBlue = Color(0.2f, 0.3f, 0.5f, 1f)

    /** A round 44-unit back button with a left arrow, the same on every portrait screen */
    fun backButton(action: () -> Unit): Actor {
        val button = ImageGetter.getImage("OtherIcons/BackArrow").apply { setSize(22f, 22f) }
            .surroundWithCircle(44f, color = buttonBlue)
        button.touchable = Touchable.enabled
        button.onActivation { action() }
        return button
    }
}
