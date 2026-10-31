package com.example.detalmick

import android.os.Bundle
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.example.detalmick.databinding.ActivityMainBinding
import com.example.detalmick.databinding.ContentAuthBinding
import com.example.detalmick.databinding.ContentHomeBinding
import com.example.detalmick.databinding.ContentProfileBinding
import com.example.detalmick.ui.AccountState
import com.example.detalmick.ui.AccountViewModel
import com.example.detalmick.ui.Screen
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var model: AccountViewModel
    private var renderedScreen: Screen? = null
    private var lastMessage = ""
    private var emailDraft = ""
    private var authForm: ContentAuthBinding? = null
    private var home: ContentHomeBinding? = null
    private var profileForm: ContentProfileBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        model = ViewModelProvider(this)[AccountViewModel::class.java]
        model.state.observe(this, ::render)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val state = model.state.value ?: return
                if (state.busy) return
                when (state.screen) {
                    Screen.REGISTER, Screen.RESET -> leaveAuth(Screen.LOGIN)
                    Screen.PROFILE -> if (!state.profileSaved) confirmLogout() else leaveProfile()
                    else -> finish()
                }
            }
        })
    }

    private fun render(state: AccountState) {
        binding.connection.setText(if (state.connected) R.string.connection_online else R.string.connection_offline)
        binding.loadingGroup.isVisible = state.busy
        binding.message.isVisible = state.message.isNotBlank()
        binding.message.text = state.message
        binding.message.setTextColor(ContextCompat.getColor(this, if (state.error) R.color.error else R.color.primary))
        binding.message.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, if (state.error) R.color.error_surface else R.color.soft))
        if (state.message.isNotBlank() && state.message != lastMessage) {
            binding.message.post {
                binding.message.requestRectangleOnScreen(Rect(0, 0, binding.message.width, binding.message.height), true)
            }
        }
        lastMessage = state.message
        if (renderedScreen != state.screen) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(binding.root.windowToken, 0)
            authForm?.password?.text?.clear()
            authForm?.confirmation?.text?.clear()
            binding.content.removeAllViews()
            authForm = null; home = null; profileForm = null
            when (state.screen) {
                Screen.LOGIN, Screen.REGISTER, Screen.RESET -> showAuth(state.screen)
                Screen.HOME -> showHome()
                Screen.PROFILE -> showProfile(state)
            }
            renderedScreen = state.screen
        }
        setEnabled(binding.content, !state.busy)
        home?.let {
            val name = state.profile?.nombre.orEmpty()
            it.greeting.text = if (name.isBlank()) getString(R.string.home_title)
                else getString(R.string.greeting, name.substringBefore(' '))
            it.fullName.text = name.ifBlank { "Tu perfil" }
            it.email.text = state.email
            it.role.setText(if (state.staff) R.string.staff_account else R.string.patient_account)
            it.edit.isEnabled = !state.busy && state.profile != null
            it.retry.isVisible = state.error || state.profile == null
            it.logout.isEnabled = true
        }
        profileForm?.let { it.logout.isEnabled = true; it.back.isVisible = state.profileSaved }
    }

    private fun showAuth(screen: Screen) {
        val form = ContentAuthBinding.inflate(layoutInflater, binding.content, true)
        authForm = form
        val register = screen == Screen.REGISTER
        val reset = screen == Screen.RESET
        form.title.setText(when (screen) { Screen.REGISTER -> R.string.register_title; Screen.RESET -> R.string.reset_title; else -> R.string.login_title })
        ViewCompat.setAccessibilityHeading(form.title, true)
        form.description.setText(when (screen) { Screen.REGISTER -> R.string.register_description; Screen.RESET -> R.string.reset_description; else -> R.string.login_description })
        form.nameLayout.isVisible = register
        form.confirmationLayout.isVisible = register
        form.passwordHelp.isVisible = register
        form.passwordLayout.isVisible = !reset
        form.forgot.isVisible = screen == Screen.LOGIN
        form.email.setText(emailDraft)
        form.submit.setText(when (screen) { Screen.REGISTER -> R.string.register; Screen.RESET -> R.string.reset; else -> R.string.login })
        form.secondary.setText(if (screen == Screen.LOGIN) R.string.register else R.string.back_login)
        form.submit.setOnClickListener {
            when (screen) {
                Screen.REGISTER -> model.register(form.name.text.toString(), form.email.text.toString(), form.password.text.toString(), form.confirmation.text.toString())
                Screen.RESET -> model.reset(form.email.text.toString())
                else -> model.login(form.email.text.toString(), form.password.text.toString())
            }
        }
        form.secondary.setOnClickListener { leaveAuth(if (screen == Screen.LOGIN) Screen.REGISTER else Screen.LOGIN) }
        form.forgot.setOnClickListener { leaveAuth(Screen.RESET) }
    }

    private fun showHome() {
        home = ContentHomeBinding.inflate(layoutInflater, binding.content, true).also {
            ViewCompat.setAccessibilityHeading(it.greeting, true)
            it.edit.setOnClickListener { model.navigate(Screen.PROFILE) }
            it.retry.setOnClickListener { model.loadProfile() }
            it.logout.setOnClickListener { confirmLogout() }
        }
    }

    private fun showProfile(state: AccountState) {
        profileForm = ContentProfileBinding.inflate(layoutInflater, binding.content, true).also {
            it.name.setText(state.profile?.nombre.orEmpty())
            it.phone.setText(state.profile?.telefono.orEmpty())
            it.address.setText(state.profile?.direccion.orEmpty())
            it.email.text = getString(R.string.account_email, state.email)
            it.save.setOnClickListener { _ -> model.save(it.name.text.toString(), it.phone.text.toString(), it.address.text.toString()) }
            it.back.setOnClickListener { leaveProfile() }
            it.logout.setOnClickListener { confirmLogout() }
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this).setTitle(R.string.logout)
            .setMessage(if (profileHasChanges()) R.string.logout_unsaved_question else R.string.logout_question)
            .setNegativeButton(R.string.stay, null)
            .setPositiveButton(R.string.logout) { _, _ -> emailDraft = ""; model.logout() }.show()
    }

    private fun leaveAuth(screen: Screen) {
        emailDraft = authForm?.email?.text.toString().trim()
        model.navigate(screen)
    }

    private fun profileHasChanges(): Boolean {
        val form = profileForm ?: return false
        val saved = model.state.value?.profile ?: return false
        return form.name.text.toString().trim() != saved.nombre ||
            form.phone.text.toString().trim() != saved.telefono ||
            form.address.text.toString().trim() != saved.direccion
    }

    private fun leaveProfile() {
        if (!profileHasChanges()) { model.navigate(Screen.HOME); return }
        MaterialAlertDialogBuilder(this).setTitle(R.string.unsaved_title)
            .setMessage(R.string.unsaved_question)
            .setNegativeButton(R.string.keep_editing, null)
            .setPositiveButton(R.string.discard) { _, _ -> model.navigate(Screen.HOME) }.show()
    }

    private fun setEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) for (index in 0 until view.childCount) setEnabled(view.getChildAt(index), enabled)
    }
}
