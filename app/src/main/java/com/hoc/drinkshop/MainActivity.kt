package com.hoc.drinkshop

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.support.transition.Fade
import android.support.transition.Slide
import android.support.transition.TransitionManager
import android.support.transition.TransitionSet
import android.support.v4.view.animation.LinearOutSlowInInterpolator
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.facebook.accountkit.AccountKit
import com.facebook.accountkit.AccountKitLoginResult
import com.facebook.accountkit.ui.AccountKitActivity
import com.facebook.accountkit.ui.AccountKitActivity.ACCOUNT_KIT_ACTIVITY_CONFIGURATION
import com.facebook.accountkit.ui.AccountKitConfiguration
import com.facebook.accountkit.ui.LoginType
import com.hoc.drinkshop.transitions.Recolor
import com.hoc.drinkshop.transitions.Scale
import com.rengwuxian.materialedittext.validation.METValidator
import com.szagurskii.patternedtextwatcher.PatternedTextWatcher
import dmax.dialog.SpotsDialog
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_register.view.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import okhttp3.ResponseBody
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.koin.android.ext.android.inject
import retrofit2.Retrofit
import javax.net.ssl.HttpsURLConnection

class MainActivity : AppCompatActivity(), AnkoLogger {
    override val loggerTag: String = "MY_TAG_MAIN"

    private val apiService by inject<ApiService>()
    private val retrofit by inject<Retrofit>()
    private val parentJob = Job()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buttonContinue.setOnClickListener {
            if (AccountKit.getCurrentAccessToken() != null) {
                info("Already login")
                checkUserIsExistAndRegister()
                return@setOnClickListener
            }

            phoneLogin()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            val fadeIn = Fade(Fade.IN)
                    .addTarget(textViewDrinkShop)
                    .setInterpolator(LinearOutSlowInInterpolator())
            val scale = Scale(0.7f)
                    .addTarget(textViewDrinkShop)
                    .setInterpolator(LinearOutSlowInInterpolator())
            val recolor = Recolor()
                    .addTarget(textViewDrinkShop)
                    .setInterpolator(LinearOutSlowInInterpolator())
            val slideEnd = Slide(Gravity.END)
                    .addTarget(buttonContinue)
                    .setInterpolator(AccelerateDecelerateInterpolator())

            TransitionSet()
                    .addTransition(fadeIn)
                    .addTransition(scale)
                    .addTransition(recolor)
                    .addTransition(slideEnd)
                    .setDuration(3_000L)
                    .let {
                        TransitionManager.beginDelayedTransition(main_layout, it)
                        textViewDrinkShop.visibility = View.VISIBLE
                        textViewDrinkShop.textColor = Color.WHITE
                        buttonContinue.visibility = View.VISIBLE
                    }
        }
    }

    private fun checkUserIsExistAndRegister() {
        launch(UI, parent = parentJob) {
            val spotsDialog = SpotsDialog(this@MainActivity, "Please wait...")
                    .apply { show() }
            val account = getCurrentAccount()
                    .onException {
                        showExceptionMessage(it)
                        spotsDialog.dismiss()
                    }
                    .getOrNull() ?: return@launch
            val phoneNumber = account.phoneNumber.phoneNumber

            if (phoneNumber == null) {
                toast("Phone number is null")
                return@launch
            }

            apiService
                    .getUserByPhone(phoneNumber)
                    .awaitResult()
                    .also { spotsDialog.dismiss() }
                    .onException {
                        showExceptionMessage(it)
                        return@launch
                    }.onError { (errorBody, response) ->
                        if (response.code() != HttpsURLConnection.HTTP_NOT_FOUND) { // if not found, then register
                            showErrorMessage(errorBody)
                            return@launch
                        }
                    }.onSuccess {
                        info(it)
                        info("User with phone $phoneNumber already exists. Navigate to home...")
                        startActivity<HomeActivity>(USER to it)
                        finish()
                        return@launch
                    }

            val view = layoutInflater.inflate(R.layout.dialog_register, null)
            val editTextBirthday = view.editTextBirthday
            val editTextAddress = view.editTextAddress
            val editTextName = view.editTextName

            editTextBirthday
                    .addValidator(object : METValidator("Invalid birthday format") {
                        override fun isValid(text: CharSequence, isEmpty: Boolean) =
                                """\d{4}-\d{2}-\d{2}""".toRegex().matches(text)
                    })
                    .addTextChangedListener(PatternedTextWatcher("####-##-##"))

            spotsDialog.dismiss()

            val alert = AlertDialog.Builder(this@MainActivity)
                    .setView(view)
                    .setTitle("Register")
                    .setMessage("Please fill in information")
                    .create()
                    .apply { show() }

            view.buttonRegister.onClick {
                val name = editTextName.text.toString()
                val birthday = editTextBirthday.text.toString()
                val address = editTextAddress.text.toString()
                var numberOfErrors = 0

                if (name.isBlank()) {
                    editTextName.error = "Blank name"
                    ++numberOfErrors
                }

                if (birthday.isBlank()) {
                    editTextBirthday.error = "Blank birthday"
                    ++numberOfErrors
                }

                if (!editTextBirthday.validate()) {
                    ++numberOfErrors
                }

                if (address.isBlank()) {
                    editTextAddress.error = "Blank address"
                    ++numberOfErrors
                }

                if (numberOfErrors == 0) {
                    alert.dismiss()
                    spotsDialog.show()

                    apiService.registerNewUser(phoneNumber, name, birthday, address)
                            .awaitResult()
                            .also { spotsDialog.dismiss() }
                            .onException(::showExceptionMessage)
                            .onError { showErrorMessage(it.first) }
                            .onSuccess {
                                startActivity<HomeActivity>(USER to it)
                                finish()
                            }
                }
            }
        }
    }

    private fun showErrorMessage(it: ResponseBody) {
        retrofit.parseResultErrorMessage(it).let { toast(it) }
    }

    private fun showExceptionMessage(it: Throwable) {
        toast(it.message ?: "An unknown error occurred")
    }

    private fun phoneLogin() {
        val configurationBuilder = AccountKitConfiguration.AccountKitConfigurationBuilder(
                LoginType.PHONE,
                AccountKitActivity.ResponseType.TOKEN
        )
        startActivityForResult<AccountKitActivity>(
                APP_REQUEST_CODE,
                ACCOUNT_KIT_ACTIVITY_CONFIGURATION to configurationBuilder.build()
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            APP_REQUEST_CODE -> {
                val loginResult = data?.getParcelableExtra<AccountKitLoginResult>(AccountKitLoginResult.RESULT_KEY)
                        ?: return
                when {
                    loginResult.error != null -> loginResult.error?.errorType?.message?.let(::toast)
                    loginResult.wasCancelled() -> toast("Login Cancelled")
                    else -> {
                        loginResult.accessToken
                                ?.let {
                                    info("Success: ${it.accountId}")
                                }
                                ?: info("Success: %s...".format(loginResult.authorizationCode?.substring(0, 10)))
                        toast("Login successfully")
                        checkUserIsExistAndRegister()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        parentJob.cancel()
    }

    companion object {
        const val APP_REQUEST_CODE = 99
        const val USER = "user"
    }
}