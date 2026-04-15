package com.xposed.wetypehook

import android.app.Activity
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.inputmethodservice.InputMethodService
import android.view.View
import com.xposed.wetypehook.wetype.hook.WeTypeResourceHooks
import com.xposed.wetypehook.wetype.hook.WeTypeWindowHooks
import com.xposed.wetypehook.wetype.settings.WeTypeSettings
import com.xposed.wetypehook.xposed.HookEnvironment
import com.xposed.wetypehook.xposed.Log
import com.xposed.wetypehook.xposed.findMethod
import com.xposed.wetypehook.xposed.hookAfter
import com.xposed.wetypehook.xposed.sameAs
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TAG = "wetypehook"
private const val WETYPE_PACKAGE = "com.tencent.wetype"
private const val WETYPE_ABOUT_ACTIVITY = "com.tencent.wetype.plugin.hld.ui.ImeAboutActivity"
private const val WETYPE_ABOUT_LOGO_ID_NAME = "ch"
private const val WETYPE_ABOUT_LOGO_TAG_KEY = 0x4D495549
private const val WETYPE_FONT_ASSET = "fonts/WE-Regular.ttf"
private const val MODULE_WETYPE_FONT_ASSET = "WE-Regular.ttf"

private val WETYPE_COLOR_REPLACEMENTS = mapOf(
    "g8" to android.graphics.Color.TRANSPARENT,
    "gb" to android.graphics.Color.TRANSPARENT,
    "k5" to android.graphics.Color.TRANSPARENT,
    "k9" to android.graphics.Color.TRANSPARENT,
    "ng" to android.graphics.Color.TRANSPARENT,
    "pq" to android.graphics.Color.TRANSPARENT
)
private val WETYPE_DRAWABLE_REPLACEMENTS = mapOf(
    "ic" to R.drawable.wetype_ic,
    "gi" to R.drawable.wetype_gi,
    "ib" to R.drawable.wetype_ib,
    "gj" to R.drawable.wetype_gj,
)

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {
    private lateinit var modulePath: String
    private var moduleAssetManager: AssetManager? = null
    private var moduleResources: Resources? = null
    private var assetManagerAddAssetPathMethod: Method? = null
    private var viewListenerInfoField: Field? = null
    private var onClickListenerField: Field? = null
    private var aboutLogoResId: Int? = null

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
        ModuleRuntime.updateModuleApkPath(startupParam.modulePath)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != WETYPE_PACKAGE) return

        HookEnvironment.init(lpparam.classLoader, TAG)
        Log.i("Hook WeType UI enhancements")
        installWeTypeHooks(WETYPE_PACKAGE)
    }

    private fun installWeTypeHooks(sourcePackage: String) {
        hookActivationHeartbeat(sourcePackage)
        WeTypeSettings.configureStorage(sourcePackage)
        WeTypeSettings.initXposed()
        hookWeTypeFont()
        hookWeTypeTransparentColors()
        hookWeTypeXmlDrawables()
        hookWeTypeSelfDrawKeyColors()
        hookWeTypeCandidateBackgroundCorner()
        hookWeTypeCandidatePinyinLeftMargin()
        hookWeTypeSettingKeyboardOpaqueBackground()
        hookWeTypeWindowBlur()
        hookWeTypeWindowCorner()
        hookWeTypeIntentEntry()
        hookWeTypeAboutLogoEntry()
    }

    private fun hookActivationHeartbeat(sourcePackage: String) {
        findMethod("android.app.Application") {
            name == "attach" && parameterTypes.sameAs(Context::class.java)
        }.hookAfter { param ->
            val context = param.args[0] as? Context ?: return@hookAfter
            notifyActivationHeartbeat(context, sourcePackage)
        }

        findMethod("android.inputmethodservice.InputMethodService") {
            name == "onStartInputView" && parameterTypes.size == 2
        }.hookAfter { param ->
            val service = param.thisObject as? InputMethodService ?: return@hookAfter
            if (service.packageName != sourcePackage) return@hookAfter
            notifyActivationHeartbeat(service, sourcePackage)
        }
    }

    private fun notifyActivationHeartbeat(context: Context, sourcePackage: String) {
        ModuleActivationTracker.notifyActivationFromHook(
            context = context,
            sourcePackage = sourcePackage,
            sourceProcess = runCatching {
                context.applicationInfo.processName ?: context.packageName
            }.getOrNull()
        )
    }

    private fun hookWeTypeFont() {
        WeTypeResourceHooks.hookFont(
            fontAsset = WETYPE_FONT_ASSET,
            moduleFontAsset = MODULE_WETYPE_FONT_ASSET,
            getModuleAssetManager = ::getModuleAssetManager
        )
    }

    private fun hookWeTypeXmlDrawables() {
        WeTypeResourceHooks.hookXmlDrawables(
            drawableReplacements = WETYPE_DRAWABLE_REPLACEMENTS,
            getModuleResources = ::getModuleResources
        )
    }

    private fun hookWeTypeTransparentColors() {
        WeTypeResourceHooks.hookAppearanceColors(WETYPE_COLOR_REPLACEMENTS)
    }

    private fun hookWeTypeSelfDrawKeyColors() {
        WeTypeResourceHooks.hookSelfDrawKeyColors()
    }

    private fun hookWeTypeCandidateBackgroundCorner() {
        WeTypeResourceHooks.hookCandidateBackgroundCorner()
    }

    private fun hookWeTypeCandidatePinyinLeftMargin() {
        WeTypeResourceHooks.hookCandidatePinyinLeftMargin()
    }

    private fun hookWeTypeSettingKeyboardOpaqueBackground() {
        WeTypeResourceHooks.hookSettingKeyboardOpaqueBackground()
    }

    private fun hookWeTypeWindowCorner() {
        WeTypeWindowHooks.hookWindowCorner()
    }

    private fun hookWeTypeWindowBlur() {
        WeTypeWindowHooks.hookWindowBlur()
    }

    private fun hookWeTypeIntentEntry() {
        runCatching {
            findMethod("android.app.Activity") {
                name == "onResume" && parameterTypes.isEmpty()
            }.hookAfter { param ->
                val activity = param.thisObject as? Activity ?: return@hookAfter
                val intent = activity.intent ?: return@hookAfter
                if (!intent.getBooleanExtra(EXTRA_OPEN_WETYPE_EMBEDDED_SETTINGS, false)) return@hookAfter
                intent.removeExtra(EXTRA_OPEN_WETYPE_EMBEDDED_SETTINGS)
                activity.window?.decorView?.post {
                    WeTypeHostLauncher.show(activity)
                }
            }
        }.onFailure {
            Log.e("Failed:Hook WeType intent entry")
            Log.i(it)
        }
    }

    private fun hookWeTypeAboutLogoEntry() {
        runCatching {
            findMethod(WETYPE_ABOUT_ACTIVITY) {
                name == "onResume" && parameterTypes.isEmpty()
            }.hookAfter { param ->
                val activity = param.thisObject as? Activity ?: return@hookAfter
                activity.window?.decorView?.post {
                    hookWeTypeAboutLogoClick(activity)
                }
            }
        }.onFailure {
            Log.e("Failed:Hook WeType about logo entry")
            Log.i(it)
        }
    }

    private fun hookWeTypeAboutLogoClick(activity: Activity) {
        runCatching {
            val logoResId = resolveAboutLogoResId(activity.resources)
            if (logoResId == 0) return

            val logoView = activity.findViewById<View>(logoResId) ?: return
            if (logoView.getTag(WETYPE_ABOUT_LOGO_TAG_KEY) == true) return

            val originalClickListener = resolveOnClickListener(logoView)
            logoView.isClickable = true
            logoView.setTag(WETYPE_ABOUT_LOGO_TAG_KEY, true)
            logoView.setOnClickListener { view ->
                runCatching {
                    originalClickListener?.onClick(view)
                }.onFailure {
                    Log.e("Failed:Invoke original WeType about logo listener")
                    Log.i(it)
                }
                WeTypeHostLauncher.show(activity)
            }
        }.onFailure {
            Log.e("Failed:Attach WeType about logo click hook")
            Log.i(it)
        }
    }

    private fun resolveAboutLogoResId(resources: Resources): Int {
        aboutLogoResId?.let { return it }
        val resolved = resources.getIdentifier(
            WETYPE_ABOUT_LOGO_ID_NAME,
            "id",
            WETYPE_PACKAGE
        )
        aboutLogoResId = resolved
        return resolved
    }

    private fun resolveOnClickListener(view: View): View.OnClickListener? {
        return runCatching {
            val listenerInfoField = viewListenerInfoField ?: View::class.java.getDeclaredField("mListenerInfo").apply {
                isAccessible = true
            }.also { viewListenerInfoField = it }
            val listenerInfo = listenerInfoField.get(view) ?: return null
            val clickListenerField = onClickListenerField ?: listenerInfo.javaClass.getDeclaredField("mOnClickListener").apply {
                isAccessible = true
            }.also { onClickListenerField = it }
            clickListenerField.get(listenerInfo) as? View.OnClickListener
        }.getOrNull()
    }

    private fun getModuleAssetManager(): AssetManager {
        moduleAssetManager?.let { return it }
        val resolvedModulePath = ModuleRuntime.resolveModuleApkPath()
            ?: modulePath.takeIf { ::modulePath.isInitialized }
            ?: error("Module apk path is unavailable")
        val assetManager = AssetManager::class.java.getDeclaredConstructor().newInstance()
        val addAssetPath = assetManagerAddAssetPathMethod ?: AssetManager::class.java.getMethod(
            "addAssetPath",
            String::class.java
        ).also { assetManagerAddAssetPathMethod = it }
        check(addAssetPath.invoke(assetManager, resolvedModulePath) as Int != 0) {
            "Failed to add module asset path: $resolvedModulePath"
        }
        moduleAssetManager = assetManager
        return assetManager
    }

    private fun getModuleResources(baseResources: Resources): Resources {
        moduleResources?.let { return it }
        return Resources(
            getModuleAssetManager(),
            baseResources.displayMetrics,
            baseResources.configuration
        ).also { moduleResources = it }
    }
}
