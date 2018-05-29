package com.hoc.drinkshop

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import com.facebook.accountkit.AccountKit
import com.facebook.accountkit.AccountKitLoginResult
import com.facebook.accountkit.ui.AccountKitActivity
import com.facebook.accountkit.ui.AccountKitActivity.ACCOUNT_KIT_ACTIVITY_CONFIGURATION
import com.facebook.accountkit.ui.AccountKitConfiguration
import com.facebook.accountkit.ui.LoginType
import com.rengwuxian.materialedittext.validation.METValidator
import com.szagurskii.patternedtextwatcher.PatternedTextWatcher
import dmax.dialog.SpotsDialog
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_register.view.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import okhttp3.ResponseBody
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.startActivityForResult
import org.jetbrains.anko.toast
import org.koin.android.ext.android.inject
import retrofit2.Retrofit


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
                toast("Already login")
                checkUserIsExistAndRegister()
                return@setOnClickListener
            }

            phoneLogin()
        }
    }

    private fun checkUserIsExistAndRegister() {
        launch(UI, parent = parentJob) {
            val spotsDialog = SpotsDialog(this@MainActivity, "Please wait...")
                    .apply { show() }

            val account = getCurrentAccount()
            val phoneNumber = account.phoneNumber.phoneNumber

            apiService.checkUserIsExist(phoneNumber)
                    .awaitResult()
                    .onException {
                        spotsDialog.dismiss()
                        showExceptionMessage(it)
                        return@launch
                    }.onError {
                        spotsDialog.dismiss()
                        showErrorMessage(it)
                        return@launch
                    }.onSuccess {
                        if (it.isExists) {
                            toast("User with phone $phoneNumber already exists. Navigate to home...")

                            val userResult = apiService.getUserInfomation(phoneNumber).awaitResult()

                            spotsDialog.dismiss()
                            userResult.onError(::showErrorMessage)
                                    .onException(::showExceptionMessage)
                                    .onSuccess {
                                        spotsDialog.dismiss()
                                        startActivity<HomeActivity>(USER to it)
                                        finish()
                                    }
                            return@launch
                        }
                    }

            val view = layoutInflater.inflate(R.layout.dialog_register, null)
            val editTextBirthday = view.editTextBirthday
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
                val name = view.editTextName.text.toString()
                val birthday = editTextBirthday.text.toString()
                val address = view.editTextAddress.text.toString()

                when {
                    name.isBlank() -> toast("Please fill in your name")
                    birthday.isBlank() -> toast("Please fill in your birthday")
                    !editTextBirthday.validate() -> toast("Invalid birthday format")
                    address.isBlank() -> toast("Please fill in your address")
                    else -> {
                        alert.dismiss()
                        spotsDialog.show()

                        val registerResult = apiService.register(phoneNumber, name, birthday, address).awaitResult()

                        spotsDialog.dismiss()

                        registerResult.onException(::showExceptionMessage)
                                .onSuccess {
                                    toast(it.message)
                                    if (it.isSuccessful) {
                                        val user = User(phoneNumber, name, birthday, address)
                                        startActivity<HomeActivity>(USER to user)
                                        finish()
                                    }
                                }.onError(::showErrorMessage)
                    }
                }
            }

        }
    }

    private fun showErrorMessage(it: ResponseBody) {
        retrofit.parse<Error>(it).let { toast(it.message) }
    }

    private fun showExceptionMessage(it: Throwable) {
        toast(it.message ?: "Unknown error")
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
                                    toast("Success: ${it.accountId}")
                                }
                                ?: toast("Success: %s...".format(loginResult.authorizationCode?.substring(0, 10)))
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
