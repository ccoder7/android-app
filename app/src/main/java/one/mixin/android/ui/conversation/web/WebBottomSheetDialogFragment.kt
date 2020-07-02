package one.mixin.android.ui.conversation.web

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebSettings.FORCE_DARK_AUTO
import android.webkit.WebSettings.FORCE_DARK_ON
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.ShareCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.tbruyelle.rxpermissions2.RxPermissions
import com.uber.autodispose.autoDispose
import kotlinx.android.synthetic.main.fragment_web.view.*
import kotlinx.android.synthetic.main.view_web_bottom.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.BuildConfig
import one.mixin.android.Constants.Mixin_Conversation_ID_HEADER
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.extension.REQUEST_CAMERA
import one.mixin.android.extension.colorFromAttribute
import one.mixin.android.extension.copyFromInputStream
import one.mixin.android.extension.createImageTemp
import one.mixin.android.extension.dpToPx
import one.mixin.android.extension.getOtherPath
import one.mixin.android.extension.getPublicPicturePath
import one.mixin.android.extension.hideKeyboard
import one.mixin.android.extension.isMixinUrl
import one.mixin.android.extension.isNightMode
import one.mixin.android.extension.isWebUrl
import one.mixin.android.extension.loadImage
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.extension.openAsUrl
import one.mixin.android.extension.openAsUrlOrQrScan
import one.mixin.android.extension.openCamera
import one.mixin.android.extension.openPermissionSetting
import one.mixin.android.extension.openUrl
import one.mixin.android.extension.statusBarHeight
import one.mixin.android.extension.supportsQ
import one.mixin.android.extension.toast
import one.mixin.android.extension.withArgs
import one.mixin.android.ui.common.MixinBottomSheetDialogFragment
import one.mixin.android.ui.common.UserBottomSheetDialogFragment
import one.mixin.android.ui.forward.ForwardActivity
import one.mixin.android.ui.qr.QRCodeProcessor
import one.mixin.android.util.Session
import one.mixin.android.util.language.Lingver
import one.mixin.android.vo.App
import one.mixin.android.vo.AppCap
import one.mixin.android.vo.AppCardData
import one.mixin.android.vo.ForwardCategory
import one.mixin.android.vo.ForwardMessage
import one.mixin.android.vo.matchResourcePattern
import one.mixin.android.widget.BottomSheet
import one.mixin.android.widget.SuspiciousLinkView
import one.mixin.android.widget.WebControlView
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.net.URISyntaxException
import java.util.concurrent.TimeUnit

class WebBottomSheetDialogFragment : MixinBottomSheetDialogFragment() {

    companion object {
        const val TAG = "WebBottomSheetDialogFragment"

        private const val FILE_CHOOSER = 0x01

        private const val CONTEXT_MENU_ID_SCAN_IMAGE = 0x11
        private const val CONTEXT_MENU_ID_SAVE_IMAGE = 0x12

        private const val URL = "url"
        private const val CONVERSATION_ID = "conversation_id"
        private const val ARGS_APP = "args_app"
        private const val ARGS_APP_CARD = "args_app_card"

        const val themeColorScript =
            """
            (function() {
                var metas = document.getElementsByTagName('meta');
                for (var i = 0; i < metas.length; i++) {
                    if (metas[i].getAttribute('name') === 'theme-color' && metas[i].hasAttribute('content')) {
                        return metas[i].getAttribute('content');
                    }
                }
                return '';
            }) ();
            """

        fun newInstance(
            url: String,
            conversationId: String?,
            app: App? = null,
            appCard: AppCardData? = null
        ) = WebBottomSheetDialogFragment().withArgs {
            putString(URL, url)
            putString(CONVERSATION_ID, conversationId)
            putParcelable(ARGS_APP, app)
            putParcelable(ARGS_APP_CARD, appCard)
        }
    }

    private val url: String by lazy {
        requireArguments().getString(URL)!!
    }
    private val conversationId: String? by lazy {
        requireArguments().getString(CONVERSATION_ID)
    }
    private var app: App? = null
    private val appCard: AppCardData? by lazy {
        requireArguments().getParcelable<AppCardData>(ARGS_APP_CARD)
    }

    private val processor = QRCodeProcessor()

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        contentView.chat_web_view.hitTestResult?.let {
            when (it.type) {
                WebView.HitTestResult.IMAGE_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    menu.add(0, CONTEXT_MENU_ID_SCAN_IMAGE, 0, R.string.contact_sq_scan_title)
                    menu.getItem(0).setOnMenuItemClickListener { menu ->
                        onContextItemSelected(menu)
                        return@setOnMenuItemClickListener true
                    }
                    menu.add(0, CONTEXT_MENU_ID_SAVE_IMAGE, 1, R.string.contact_save_image)
                    menu.getItem(1).setOnMenuItemClickListener { menu ->
                        onContextItemSelected(menu)
                        return@setOnMenuItemClickListener true
                    }
                }
                else -> Timber.d("App does not yet handle target type: ${it.type}")
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        contentView.chat_web_view.hitTestResult?.let {
            val url = it.extra
            if (item.itemId == CONTEXT_MENU_ID_SCAN_IMAGE) {
                lifecycleScope.launch {
                    try {
                        val bitmap = withContext(Dispatchers.IO) {
                            Glide.with(requireContext())
                                .asBitmap()
                                .load(url)
                                .submit()
                                .get(10, TimeUnit.SECONDS)
                        }
                        if (isDetached) return@launch

                        processor.detect(
                            lifecycleScope,
                            bitmap,
                            onSuccess = { result ->
                                result.openAsUrlOrQrScan(parentFragmentManager, lifecycleScope)
                            },
                            onFailure = {
                                if (isAdded) toast(R.string.can_not_recognize)
                            }
                        )
                    } catch (e: Exception) {
                        if (isAdded) toast(R.string.can_not_recognize)
                    }
                }
                return true
            } else if (item.itemId == CONTEXT_MENU_ID_SAVE_IMAGE) {
                saveImageFromUrl(url)
            }
        }
        return super.onContextItemSelected(item)
    }

    var uploadMessage: ValueCallback<Array<Uri>>? = null

    @SuppressLint("RestrictedApi", "SetJavaScriptEnabled")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        contentView = View.inflate(context, R.layout.fragment_web, null)
        val statusBarHeight = requireContext().statusBarHeight()
        contentView.ph.updateLayoutParams<ViewGroup.LayoutParams> {
            height = statusBarHeight
        }
        contentView.web_control.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = requireContext().dpToPx(6f) + statusBarHeight
        }
        registerForContextMenu(contentView.chat_web_view)
        (dialog as BottomSheet).apply {
            setCustomView(contentView)
        }

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        app = requireArguments().getParcelable<App>(ARGS_APP)

        initView()

        appCard.notNullWithElse(
            {
                checkAppCard(it)
            },
            {
                loadWebView()
            }
        )
    }

    private fun checkAppCard(appCard: AppCardData) = lifecycleScope.launch {
        if (!isAdded) return@launch

        if (appCard.appId != null) {
            app = bottomViewModel.getAppAndCheckUser(appCard.appId, appCard.updatedAt)
            if (app.matchResourcePattern(url)) {
                controlSuspiciousView(false)
                loadWebView()
            } else {
                controlSuspiciousView(true)
            }
        } else {
            loadWebView()
        }
    }

    private fun controlSuspiciousView(show: Boolean) {
        contentView.suspicious_link_view.isVisible = show
        if (show) {
            contentView.pb.isVisible = false
        }
    }

    private fun initView() {
        contentView.suspicious_link_view.listener = object : SuspiciousLinkView.SuspiciousListener {
            override fun onBackClick() {
                dismiss()
            }

            override fun onContinueClick() {
                loadWebView()
                controlSuspiciousView(false)
            }
        }
        contentView.web_control.callback = object : WebControlView.Callback {
            override fun onMoreClick() {
                showBottomSheet()
            }

            override fun onCloseClick() {
                dismiss()
            }
        }
        contentView.chat_web_view.settings.javaScriptEnabled = true
        contentView.chat_web_view.settings.domStorageEnabled = true
        contentView.chat_web_view.settings.useWideViewPort = true
        contentView.chat_web_view.settings.loadWithOverviewMode = true
        supportsQ {
            contentView.chat_web_view.settings.forceDark = if (requireContext().isNightMode()) {
                FORCE_DARK_ON
            } else {
                FORCE_DARK_AUTO
            }
        }
        contentView.chat_web_view.settings.mixedContentMode =
            WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        contentView.chat_web_view.settings.mediaPlaybackRequiresUserGesture = false
        contentView.chat_web_view.settings.userAgentString =
            contentView.chat_web_view.settings.userAgentString + " Mixin/" + BuildConfig.VERSION_NAME

        contentView.chat_web_view.webViewClient =
            WebViewClientImpl(
                object : WebViewClientImpl.OnPageFinishedListener {
                    override fun onPageFinished() {
                        reloadTheme()
                    }
                },
                conversationId, this.parentFragmentManager, lifecycleScope
            )

        contentView.chat_web_view.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!isBot()) {
                    contentView.title_tv.text = title
                }
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                if (!isBot()) {
                    icon?.let {
                        contentView.icon_iv.isVisible = true
                        contentView.icon_iv.setImageBitmap(it)
                    }
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let {
                    for (code in request.resources) {
                        if (code != PermissionRequest.RESOURCE_VIDEO_CAPTURE && code != PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                            request.deny()
                            return@let
                        }
                    }
                    request.grant(request.resources)
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                uploadMessage?.onReceiveValue(null)
                uploadMessage = filePathCallback
                val intent: Intent? = fileChooserParams?.createIntent()
                if (fileChooserParams?.isCaptureEnabled == true) {
                    if (intent?.type == "video/*") {
                        PermissionBottomSheetDialogFragment.requestVideo(
                            contentView.title_tv.text.toString(),
                            app?.name,
                            app?.iconUrl
                        )
                            .setCancelAction {
                                uploadMessage?.onReceiveValue(null)
                                uploadMessage = null
                            }
                            .setGrantedAction {
                                RxPermissions(requireActivity())
                                    .request(Manifest.permission.CAMERA)
                                    .autoDispose(stopScope)
                                    .subscribe(
                                        { granted ->
                                            if (granted) {
                                                startActivityForResult(
                                                    Intent(MediaStore.ACTION_VIDEO_CAPTURE),
                                                    FILE_CHOOSER
                                                )
                                            } else {
                                                context?.openPermissionSetting()
                                            }
                                        },
                                        {
                                        }
                                    )
                            }.show(parentFragmentManager, PermissionBottomSheetDialogFragment.TAG)
                        return true
                    } else if (intent?.type == "image/*") {
                        PermissionBottomSheetDialogFragment.requestCamera(
                            contentView.title_tv.text.toString(),
                            app?.name,
                            app?.iconUrl
                        )
                            .setCancelAction {
                                uploadMessage?.onReceiveValue(null)
                                uploadMessage = null
                            }.setGrantedAction {
                                RxPermissions(requireActivity())
                                    .request(Manifest.permission.CAMERA)
                                    .autoDispose(stopScope)
                                    .subscribe(
                                        { granted ->
                                            if (granted) {
                                                openCamera(getImageUri())
                                            } else {
                                                context?.openPermissionSetting()
                                            }
                                        },
                                        {
                                        }
                                    )
                            }.show(parentFragmentManager, PermissionBottomSheetDialogFragment.TAG)
                        return true
                    }
                }
                try {
                    startActivityForResult(intent, FILE_CHOOSER)
                } catch (e: ActivityNotFoundException) {
                    uploadMessage = null
                    toast(R.string.error_file_chooser)
                    return false
                }
                return true
            }
        }

        dialog?.setOnDismissListener {
            contentView.hideKeyboard()
            contentView.chat_web_view.stopLoading()
            contentView.chat_web_view.webViewClient = null
            contentView.chat_web_view.webChromeClient = null
        }
    }

    @Override
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CAMERA) {
            imageUri?.let {
                uploadMessage?.onReceiveValue(arrayOf(it))
                imageUri = null
            }
            uploadMessage = null
        } else if (requestCode == FILE_CHOOSER && resultCode == Activity.RESULT_OK) {
            uploadMessage?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(
                    resultCode,
                    data
                )
            )
            uploadMessage = null
        } else {
            uploadMessage?.onReceiveValue(null)
            uploadMessage = null
        }
    }

    private fun loadWebView() {
        contentView.pb.isVisible = false

        var immersive = false
        app?.capabilities?.let {
            if (it.contains(AppCap.IMMERSIVE.name)) {
                immersive = true
            }
        }
        app?.name?.let { contentView.title_tv.text = it }
        app?.iconUrl?.let {
            contentView.icon_iv.isVisible = true
            contentView.icon_iv.loadImage(it)
            contentView.title_tv.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = requireContext().dpToPx(10f)
            }
        }
        contentView.title_ll.isGone = immersive

        contentView.chat_web_view.addJavascriptInterface(
            WebAppInterface(
                requireContext(),
                conversationId,
                immersive,
                reloadThemeAction = { reloadTheme() }
            ),
            "MixinContext"
        )

        val extraHeaders = HashMap<String, String>()
        conversationId?.let {
            extraHeaders[Mixin_Conversation_ID_HEADER] = it
        }
        contentView.chat_web_view.loadUrl(url, extraHeaders)
    }

    private fun reloadTheme() {
        if (!isAdded) return

        lifecycleScope.launch {
            contentView.chat_web_view.evaluateJavascript(themeColorScript) {
                setStatusBarColor(it)
            }
        }
    }

    private var imageUri: Uri? = null
    private fun getImageUri(): Uri {
        if (imageUri == null) {
            imageUri = Uri.fromFile(requireContext().getOtherPath().createImageTemp())
        }
        return imageUri!!
    }

    private fun isBot() = app != null

    override fun onDestroyView() {
        contentView.chat_web_view.stopLoading()
        contentView.chat_web_view.destroy()
        contentView.chat_web_view.webViewClient = null
        contentView.chat_web_view.webChromeClient = null
        unregisterForContextMenu(contentView.chat_web_view)
        processor.close()
        super.onDestroyView()
    }

    private fun showBottomSheet() {
        if (!isAdded) return

        val builder = BottomSheet.Builder(requireActivity())
        val view = View.inflate(
            ContextThemeWrapper(requireActivity(), R.style.Custom),
            R.layout.view_web_bottom,
            null
        )
        builder.setCustomView(view)
        val bottomSheet = builder.create()
        view.forward.setOnClickListener {
            val currentUrl = contentView.chat_web_view.url
            if (isBot()) {
                if (app?.appId == null) return@setOnClickListener

                lifecycleScope.launch {
                    val app = bottomViewModel.getAppAndCheckUser(app?.appId!!, app?.updatedAt)
                    if (app.matchResourcePattern(currentUrl)) {
                        val webTitle = contentView.chat_web_view.title ?: app.name
                        val appCardData = AppCardData(app.appId, app.iconUrl, webTitle, app.name, currentUrl, app.updatedAt)
                        ForwardActivity.show(
                            requireContext(),
                            arrayListOf(ForwardMessage(ForwardCategory.APP_CARD.name, content = Gson().toJson(appCardData)))
                        )
                    } else {
                        ForwardActivity.show(requireContext(), currentUrl)
                    }
                }
            } else {
                ForwardActivity.show(requireContext(), currentUrl)
            }
            bottomSheet.dismiss()
        }
        view.share.setOnClickListener {
            if (isBot()) {
                openBot()
            } else {
                activity?.let {
                    ShareCompat.IntentBuilder
                        .from(it)
                        .setType("text/plain")
                        .setChooserTitle(contentView.chat_web_view.title)
                        .setText(contentView.chat_web_view.url)
                        .startChooser()
                    bottomSheet.dismiss()
                }
            }
            bottomSheet.dismiss()
        }
        view.refresh.setOnClickListener {
            contentView.chat_web_view.clearCache(true)
            contentView.chat_web_view.reload()
            bottomSheet.dismiss()
        }
        view.open.setOnClickListener {
            contentView.chat_web_view.url?.let {
                context?.openUrl(it)
            }
            bottomSheet.dismiss()
        }
        if (isBot()) {
            view.open.isVisible = false
            view.share.text = getString(R.string.about)
        }

        bottomSheet.show()
    }

    private fun openBot() = lifecycleScope.launch {
        if (!isAdded) return@launch

        if (app?.appId != null) {
            val u = bottomViewModel.suspendFindUserById(app?.appId!!)
            if (u != null) {
                UserBottomSheetDialogFragment.newInstance(u, conversationId)
                    .showNow(parentFragmentManager, UserBottomSheetDialogFragment.TAG)
            }
        }
    }

    override fun onPause() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !requireActivity().isInMultiWindowMode) {
            contentView.chat_web_view.onResume()
            contentView.chat_web_view.resumeTimers()
        }
        super.onPause()
    }

    override fun onResume() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !requireActivity().isInMultiWindowMode) {
            contentView.chat_web_view.onResume()
            contentView.chat_web_view.resumeTimers()
        }
        super.onResume()
    }

    private fun saveImageFromUrl(url: String?) {
        if (!isAdded) return
        RxPermissions(requireActivity())
            .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .autoDispose(stopScope)
            .subscribe { granted ->
                if (granted) {
                    doAsync {
                        try {
                            val outFile = requireContext().getPublicPicturePath()
                                .createImageTemp(noMedia = false)
                            val encodingPrefix = "base64,"
                            val prefixIndex = url?.indexOf(encodingPrefix)
                            if (url != null && prefixIndex != null && prefixIndex != -1) {
                                val dataStartIndex = prefixIndex + encodingPrefix.length
                                val imageData =
                                    Base64.decode(url.substring(dataStartIndex), Base64.DEFAULT)
                                outFile.copyFromInputStream(ByteArrayInputStream(imageData))
                            } else {
                                val file = Glide.with(MixinApplication.appContext)
                                    .asFile()
                                    .load(url)
                                    .submit()
                                    .get(10, TimeUnit.SECONDS)
                                outFile.copyFromInputStream(FileInputStream(file))
                            }
                            requireContext().sendBroadcast(
                                Intent(
                                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                    Uri.fromFile(outFile)
                                )
                            )
                            uiThread { if (isAdded) toast(getString(R.string.save_to, outFile.absolutePath)) }
                        } catch (e: Exception) {
                            uiThread { if (isAdded) toast(R.string.save_failure) }
                        }
                    }
                } else {
                    context?.openPermissionSetting()
                }
            }
    }

    private fun setStatusBarColor(content: String) {
        try {
            val color = content.replace("\"", "")
            val c = Color.parseColor(color)
            val dark = ColorUtils.calculateLuminance(c) < 0.5
            refreshByLuminance(dark, c)
        } catch (e: Exception) {
            context?.let {
                refreshByLuminance(it.isNightMode(), it.colorFromAttribute(R.attr.icon_white))
            }
        }
    }

    private fun refreshByLuminance(
        dark: Boolean,
        color: Int
    ) {
        dialog?.window?.decorView?.let {
            if (dark) {
                contentView.title_tv.setTextColor(Color.WHITE)
                it.systemUiVisibility =
                    it.systemUiVisibility and SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                contentView.title_tv.setTextColor(Color.BLACK)
                it.systemUiVisibility = it.systemUiVisibility or SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
        contentView.title_ll.setBackgroundColor(color)
        contentView.ph.setBackgroundColor(color)
        contentView.web_control.mode = dark
    }

    @Suppress("DEPRECATION")
    class WebViewClientImpl(
        private val onPageFinishedListener: OnPageFinishedListener,
        val conversationId: String?,
        private val fragmentManager: FragmentManager,
        private val scope: CoroutineScope
    ) : WebViewClient() {

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            onPageFinishedListener.onPageFinished()
        }

        override fun onPageCommitVisible(view: WebView?, url: String?) {
            super.onPageCommitVisible(view, url)
            onPageFinishedListener.onPageFinished()
        }

        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (view == null || url == null) {
                return super.shouldOverrideUrlLoading(view, url)
            }
            if (url.isMixinUrl()) {
                url.openAsUrl(fragmentManager, scope) {}
                return true
            }
            val extraHeaders = HashMap<String, String>()
            conversationId?.let {
                extraHeaders[Mixin_Conversation_ID_HEADER] = it
            }
            if (url.isWebUrl()) {
                view.loadUrl(url, extraHeaders)
                return true
            } else {
                try {
                    val context = view.context
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)

                    if (intent != null) {
                        val packageManager = context.packageManager
                        val info = packageManager.resolveActivity(
                            intent,
                            PackageManager.MATCH_DEFAULT_ONLY
                        )
                        if (info != null) {
                            view.stopLoading()
                            context.startActivity(intent)
                        }
                    }
                } catch (e: URISyntaxException) {
                    view.loadUrl(url, extraHeaders)
                } catch (e: ActivityNotFoundException) {
                    view.loadUrl(url, extraHeaders)
                }
            }
            return true
        }

        interface OnPageFinishedListener {
            fun onPageFinished()
        }
    }

    class WebAppInterface(
        val context: Context,
        val conversationId: String?,
        val immersive: Boolean,
        val reloadThemeAction: () -> Unit
    ) {
        @JavascriptInterface
        fun showToast(toast: String) {
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
        }

        @JavascriptInterface
        fun getContext(): String? = Gson().toJson(
            MixinContext(
                conversationId, immersive,
                appearance = if (context.isNightMode()) {
                    "dark"
                } else {
                    "light"
                }
            )
        )

        @JavascriptInterface
        fun reloadTheme() {
            reloadThemeAction.invoke()
        }
    }

    class MixinContext(
        @SerializedName("conversation_id")
        val conversationId: String?,
        @SerializedName("immersive")
        val immersive: Boolean,
        @SerializedName("app_version")
        val appVersion: String = BuildConfig.VERSION_NAME,
        @SerializedName("appearance")
        val appearance: String,
        @SerializedName("platform")
        val platform: String = "Android",
        @SerializedName("currency")
        val currency: String = Session.getFiatCurrency(),
        @SerializedName("locale")
        val locale: String = "${Lingver.getInstance().getLocale().language}-${Lingver.getInstance().getLocale().country}"
    )
}
