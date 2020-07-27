package io.ikws4.weiju.xposed

import android.annotation.SuppressLint
import android.location.Location
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import dalvik.system.DexClassLoader
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.ikws4.library.xposedktx.hookMethod
import io.ikws4.library.xposedktx.replaceMethod
import io.ikws4.library.xposedktx.setStaticObjectField
import io.ikws4.weiju.utilities.XSPUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class VariableHook(private val sp: XSPUtils, private val classLoader: ClassLoader) {
    companion object {
        private const val TAG = "VariableHook"
    }

    private val isEnable = sp.getBoolean("is_enable_variable")
    private val device = sp.getString("variable_device")
    private val productName = sp.getString("variable_product_name")
    private val model = sp.getString("variable_model")
    private val brand = sp.getString("variable_brand")
    private val release = sp.getString("variable_release")
    private val imei = sp.getString("variable_imei")
    private val imsi = sp.getString("variable_imsi")

    private val longitude: Double?
        get() {
            return sp.getString("variable_longitude").toDoubleOrNull()
        }
    private val latitude: Double?
        get() {
            return sp.getString("variable_latitude").toDoubleOrNull()
        }
    private var isLocationBaidu: Boolean? = sp.getBoolean("is_location_baidu")
    private var isLocationTx: Boolean? = sp.getBoolean("is_location_tx")
    private var isLocationTxFound = false

    init {
        if (isEnable) {
            replaceBuildConfig()
            replaceLocationInfo()
            replaceImei()
            replaceImsi()
        }
    }

    private fun replaceBuildConfig() {

        with(Build::class.java) {
            // 设备代号
            // ro.product.device=mido
            setStaticObjectField("DEVICE", device)

            // ro.product.name=aosp_mido
            setStaticObjectField("PRODUCT", productName)

            // 机型
            // ro.product.model=Redmi Note 4
            setStaticObjectField("MODEL", model)

            // 厂商
            // ro.product.brand=xiaomi
            // ro.product.manufacturer=xiaomi
            setStaticObjectField("BRAND", brand)
            setStaticObjectField("MANUFACTURER", brand)
        }
        // 安卓版本
        // ro.build.version.release=9
        Build.VERSION::class.java.setStaticObjectField("RELEASE", release)
    }

    private fun replaceLocationInfo() {
        Location::class.java.hookMethod("getLongitude") { param ->
            try {
                param.result = this@VariableHook.longitude ?: param.result
            } catch (e: Exception) {
                MainHook.log(e)
            }
        }
        Location::class.java.hookMethod("getLatitude") { param ->
            try {
                param.result = this@VariableHook.latitude ?: param.result
            } catch (e: Exception) {
                MainHook.log(e)
            }
        }

        try {
            hookLocationBaidu()
        } catch (e: java.lang.Exception) {
            //ignore
        }
        try {
            hookLocationTencent()
        } catch (e: java.lang.Exception) {
            //ignore
        }
    }

    private fun hookLocationBaidu() {
        if (isLocationBaidu != true) return
        // 百度
        val bdLocation = "com.baidu.location.BDLocation"
        bdLocation.hookMethod(classLoader, "getLongitude") { param ->
            try {
                param.result = this@VariableHook.longitude ?: param.result
            } catch (e: Exception) {
                MainHook.log(e)
            }
        }
        bdLocation.hookMethod(classLoader, "getLatitude") { param ->
            try {
                param.result = this@VariableHook.latitude ?: param.result
            } catch (e: Exception) {
                MainHook.log(e)
            }
        }
        isLocationBaidu = null
    }

    val locationTxUnhook = HashSet<XC_MethodHook.Unhook>()

    private fun hookLocationTencent() {
        if (isLocationTx != true) return
        Log.d(TAG, "hookLocationTencent isLocationTx $isLocationTx")

        //腾讯 com.tencent.map.geolocation.TencentLocation
        // 这里查看版本/data/data/com.tencent.mm/files/TencentLocation/conf
        // 6.6.1版本的腾讯地图sdk 实现类为 c.t.m.g.gi
        val unhook = XposedBridge.hookAllMethods(ClassLoader::class.java, "loadClass", object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                Log.d(TAG, "hookLocationTencent afterHookedMethod $isLocationTxFound")
                if (isLocationTxFound) return
                if (param.thisObject !is DexClassLoader) return
                if (param.hasThrowable()) return
                if (param.args.size != 1) return
                val cls = param.result as Class<*>
                val name = cls.name
                val classLoader = param.thisObject as ClassLoader
                var found = false
                for (type in cls.interfaces) {
                    if (name == "c.t.m.g.gi") {
                        Log.d(TAG, "hookLocationTencent: " + type.name)
                    }
                    if (type.name == "com.tencent.map.geolocation.TencentLocation") {
                        found = true
                        break
                    }
                }
                if (found) {
                    val it = locationTxUnhook.iterator()
                    while (it.hasNext()) {
                        val unhook = it.next();
                        unhook.unhook()
                        XposedBridge.unhookMethod(param.method, this)
                        it.remove()
                    }

                    isLocationTxFound = true
                    Log.d(TAG, "hookLocationTencent: \"TencentLocation\" --> $name")
                    name.hookMethod(classLoader, "getLatitude") { latitudeParam ->
                        try {
                            latitudeParam.result = this@VariableHook.latitude ?: latitudeParam.result
                            Log.d(TAG, "replaceLocationInfo: " + latitudeParam.result)
                        } catch (e: Exception) {
                            MainHook.log(e)
                        }
                    }
                    name.hookMethod(classLoader, "getLongitude") { longitudeParam ->
                        try {
                            longitudeParam.result = this@VariableHook.longitude ?: longitudeParam.result
                            Log.d(TAG, "replaceLocationInfo: " + longitudeParam.result)
                        } catch (e: Exception) {
                            MainHook.log(e)
                        }
                    }
                }
            }
        })
        Log.d(TAG, "locationTxUnhook: addAll " + unhook?.size)
        locationTxUnhook.addAll(unhook)
    }

    @SuppressLint("MissingPermission")
    private fun replaceImei() {
        if (imei == "error: not permission") return

        with(TelephonyManager::class.java) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                replaceMethod("getImei") {
                    return@replaceMethod this@VariableHook.imei
                }
            } else {
                replaceMethod("getDeviceId") {
                    return@replaceMethod this@VariableHook.imei
                }
            }
        }
    }

    private fun replaceImsi() {
        if (imsi == "error: not permission") return

        TelephonyManager::class.java.replaceMethod("getSubscriberId") {
            return@replaceMethod imsi
        }
    }
}
