package com.github.damontecres.stashapp

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.github.damontecres.stashapp.util.PresencePinUnlock
import com.github.damontecres.stashapp.util.StashCoroutineExceptionHandler
import com.github.damontecres.stashapp.views.models.ServerViewModel
import kotlinx.coroutines.launch

class PinFragment : Fragment(R.layout.pin_dialog) {
    private val serverViewModel: ServerViewModel by activityViewModels<ServerViewModel>()

    private lateinit var pinEditText: EditText
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pinCode =
            PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getString("pinCode", "")
        if (pinCode.isNullOrBlank()) {
            unlocked = true
            // startMain needs the fragment to be attached; defer to onViewCreated
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        pinEditText = view.findViewById(R.id.pin_edit_text)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val pinCode = prefs.getString(getString(R.string.pref_key_pin_code), "")

        if (unlocked || pinCode.isNullOrBlank()) {
            startMain()
            return
        }

        val submit = view.findViewById<Button>(R.id.pin_submit)
        submit.setOnClickListener {
            val enteredPin = pinEditText.text.toString()
            if (enteredPin == pinCode) {
                startMain()
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
                    startMain()
                }
            }
        }

        val presenceConfig = PresencePinUnlock.configFrom(prefs, requireContext())
        if (presenceConfig.isConfigured) {
            // Hide PIN UI while checking presence so unlock feels instant.
            view.visibility = View.INVISIBLE
            viewLifecycleOwner.lifecycleScope.launch(StashCoroutineExceptionHandler()) {
                val skip = PresencePinUnlock.shouldSkipPin(requireContext())
                if (!isAdded || unlocked) return@launch
                if (skip) {
                    unlocked = true
                    startMain()
                } else {
                    view.visibility = View.VISIBLE
                    pinEditText.requestFocus()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::pinEditText.isInitialized || unlocked) return
        if (view?.visibility != View.VISIBLE) return
        pinEditText.requestFocus()
        val imm = getSystemService(requireContext(), InputMethodManager::class.java)!!
        imm.showSoftInput(pinEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onStop() {
        super.onStop()
        if (!::pinEditText.isInitialized) return
        val imm = getSystemService(requireContext(), InputMethodManager::class.java)!!
        imm.hideSoftInputFromWindow(pinEditText.windowToken, 0)
    }

    private fun startMain() {
        unlocked = true
        serverViewModel.navigationManager.clearPinFragment()
    }
}
