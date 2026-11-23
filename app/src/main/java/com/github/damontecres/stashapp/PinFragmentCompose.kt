package com.github.damontecres.stashapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.github.damontecres.stashapp.util.PresencePinUnlock
import com.github.damontecres.stashapp.util.StashCoroutineExceptionHandler
import kotlinx.coroutines.launch

class PinFragmentCompose : Fragment(R.layout.pin_dialog) {
    private lateinit var pinEditText: EditText
    private var unlocked = false

    var onCorrectPin: () -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("RootPin", "onCreate")
        super.onCreate(savedInstanceState)
        val pinCode =
            PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getString("pinCode", "")
        if (pinCode.isNullOrBlank()) {
            unlocked = true
            onCorrectPin.invoke()
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        Log.i("RootPin", "onViewCreated")
        super.onViewCreated(view, savedInstanceState)

        pinEditText = view.findViewById(R.id.pin_edit_text)
        pinEditText.setText("")

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val pinCode = prefs.getString(getString(R.string.pref_key_pin_code), "")

        if (unlocked || pinCode.isNullOrBlank()) {
            return
        }

        val submit = view.findViewById<Button>(R.id.pin_submit)
        submit.setOnClickListener {
            val enteredPin = pinEditText.text.toString()
            if (enteredPin == pinCode) {
                unlocked = true
                onCorrectPin.invoke()
            } else {
                Toast.makeText(requireContext(), "Wrong PIN", Toast.LENGTH_SHORT).show()
            }
        }

        val autoSubmitPin =
            prefs.getBoolean(getString(R.string.pref_key_pin_code_auto), false)
        if (autoSubmitPin) {
            submit.visibility = View.INVISIBLE
            pinEditText.doAfterTextChanged {
                val enteredPin = it.toString()
                if (enteredPin == pinCode) {
                    unlocked = true
                    onCorrectPin.invoke()
                }
            }
        }

        val presenceConfig = PresencePinUnlock.configFrom(prefs, requireContext())
        if (presenceConfig.isConfigured) {
            view.visibility = View.INVISIBLE
            viewLifecycleOwner.lifecycleScope.launch(StashCoroutineExceptionHandler()) {
                val skip = PresencePinUnlock.shouldSkipPin(requireContext())
                if (!isAdded || unlocked) return@launch
                if (skip) {
                    unlocked = true
                    onCorrectPin.invoke()
                } else {
                    view.visibility = View.VISIBLE
                    pinEditText.requestFocus()
                }
            }
        }
    }

    override fun onResume() {
        Log.i("RootPin", "onResume")
        super.onResume()
        requireView().visibility = View.VISIBLE
        pinEditText.requestFocus()
        val imm = getSystemService(requireContext(), InputMethodManager::class.java)!!
        imm.showSoftInput(pinEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onStop() {
        Log.i("RootPin", "onStop")
        super.onStop()
        val imm = getSystemService(requireContext(), InputMethodManager::class.java)!!
        imm.hideSoftInputFromWindow(pinEditText.windowToken, 0)
        pinEditText.setText("")
    }
}
