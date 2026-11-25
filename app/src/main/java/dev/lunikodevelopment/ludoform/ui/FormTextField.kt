package dev.lunikodevelopment.ludoform.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.cookieFocus

/**
 * A text field on the Stage that only takes the keyboard while [editing] (as the RomM form does): the gamepad picks
 * it, A or a touch starts typing, Up/Down/A/B stop. [anyEditing] false hides the keyboard.
 */
@Composable
fun FormTextField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    focused: Boolean,
    editing: Boolean,
    anyEditing: Boolean,
    onStartEditing: () -> Unit,
    onNext: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Ascii,
    password: Boolean = false,
    last: Boolean = false,
    height: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val colors = Ludo.colors
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(editing) {
        if (editing) {
            requester.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(anyEditing) {
        if (!anyEditing) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = LudoType.caption, color = if (focused) colors.lav else colors.on2)
        Box(
            Modifier
                .cookieFocus(focused && !editing, ExpressiveShapes.wavyLarge, colors.lav, outset = 5.dp, scale = 1.02f)
                .fillMaxWidth()
                .height(height)
                .background(colors.sf2, shape)
                .border(if (editing) 2.dp else 0.dp, if (editing) colors.lav else colors.sf2, shape)
                .pointerInput(label) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (!editing) onStartEditing()
                    }
                }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) Text(placeholder, style = LudoType.label, color = colors.on2.copy(alpha = 0.7f))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().focusProperties { canFocus = editing }.focusRequester(requester),
                singleLine = true,
                textStyle = LudoType.label.copy(color = colors.on, fontSize = 17.sp),
                cursorBrush = SolidColor(colors.lav),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = if (password) KeyboardType.Password else keyboardType,
                        imeAction = if (last) ImeAction.Done else ImeAction.Next,
                    ),
                keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onNext() }),
            )
        }
    }
}
