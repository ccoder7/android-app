package one.mixin.android.ui.common.biometric

import android.content.Context
import android.os.CancellationSignal
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import androidx.biometric.BiometricConstants
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.crashlytics.android.Crashlytics
import java.nio.charset.Charset
import java.security.InvalidKeyException
import one.mixin.android.Constants
import one.mixin.android.R
import one.mixin.android.crypto.Base64
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.util.BiometricUtil
import org.jetbrains.anko.getStackTraceString
import org.jetbrains.anko.toast

class BiometricInfo(
    val title: String,
    val subTitle: String,
    val description: String,
    val negativeBtnText: String
)

class BiometricDialog(
    private val context: Context,
    private val fragment: Fragment,
    private val biometricInfo: BiometricInfo
) {
    var callback: Callback? = null
    private var cancellationSignal: CancellationSignal? = null

    fun show() {
        val executor = ContextCompat.getMainExecutor(context)
        val authCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricConstants.ERROR_CANCELED || errorCode == BiometricConstants.ERROR_USER_CANCELED) {
                    callback?.onCancel()
                } else if (errorCode == BiometricConstants.ERROR_LOCKOUT ||
                    errorCode == BiometricConstants.ERROR_LOCKOUT_PERMANENT) {
                    cancellationSignal?.cancel()
                    callback?.showPin()
                } else if (errorCode == BiometricConstants.ERROR_NEGATIVE_BUTTON) {
                    callback?.showPin()
                } else {
                    context.toast(errString)
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val cipher = result.cryptoObject?.cipher
                if (cipher != null) {
                    try {
                        val encrypt = context.defaultSharedPreferences.getString(Constants.BIOMETRICS_PIN, null)
                        val decryptByteArray = cipher.doFinal(Base64.decode(encrypt, Base64.URL_SAFE))
                        val pin = decryptByteArray.toString(Charset.defaultCharset())
                        callback?.onPinComplete(pin)
                    } catch (e: Exception) {
                        Crashlytics.log(Log.ERROR, BiometricUtil.CRASHLYTICS_BIOMETRIC,
                            "onAuthenticationSucceeded  ${e.getStackTraceString()}")
                    }
                }
            }
        }
        val biometricPrompt = BiometricPrompt(fragment, executor, authCallback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(biometricInfo.title)
            .setSubtitle(biometricInfo.subTitle)
            .setDescription(biometricInfo.description)
            .setNegativeButtonText(biometricInfo.negativeBtnText)
            .setConfirmationRequired(true)
            .build()

        val cipher = try {
            BiometricUtil.getDecryptCipher(context)
        } catch (e: Exception) {
            when (e) {
                is UserNotAuthenticatedException -> callback?.showAuthenticationScreen()
                is InvalidKeyException, is NullPointerException -> {
                    BiometricUtil.deleteKey(context)
                    context.toast(R.string.wallet_biometric_invalid)
                    callback?.onCancel()
                }
                else ->
                    Crashlytics.log(Log.ERROR, BiometricUtil.CRASHLYTICS_BIOMETRIC, "getDecryptCipher. ${e.getStackTraceString()}")
            }
            return
        }

        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    interface Callback {
        fun onPinComplete(pin: String)

        fun showPin()

        fun showAuthenticationScreen()

        fun onCancel()
    }
}
