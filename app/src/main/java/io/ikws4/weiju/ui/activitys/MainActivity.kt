package io.ikws4.weiju.ui.activitys

import android.Manifest
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.findNavController
import io.ikws4.weiju.R
import io.ikws4.weiju.databinding.ActivityMainBinding
import io.ikws4.weiju.ui.fragments.MainHomeFragmentDirections
import io.ikws4.weiju.ui.viewmodels.UserViewModel
import io.ikws4.weiju.utilities.InjectorUtils
import io.ikws4.weiju.utilities.SPManager

class MainActivity : BasicActivity() {
    private lateinit var navController: NavController
    private lateinit var binding: ActivityMainBinding
    private val viewModel by viewModels<UserViewModel> { InjectorUtils.provideUserViewModelFactory(this) }
    private val spManager by lazy { SPManager.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        navController = findNavController(R.id.nav_host_fragment)
        // 更新数据
        viewModel.user.observe(this, Observer { user ->
            spManager.WeiJuSP().freeSogouApiAmount = user?.freeSogouApiAmount ?: 0
        })
        setupToolbar()
        // 获取电话权限，用于读取IMEI及IMSI
        getPermission(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    private fun setupToolbar() {
        with(binding) {
            val margin = resources.getDimensionPixelSize(R.dimen.normal)
            toolbar.setPadding(0, 0, margin, 0)
            setSupportActionBar(toolbar)
            toolbarTitle.text = getString(R.string.app_name)
            supportActionBar!!.setDisplayShowTitleEnabled(false)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_activity_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_setting -> {
                navController.navigate(
                    MainHomeFragmentDirections
                        .toSettingActivity(SettingActivity.HOME)
                )
            }
        }
        return true
    }

    companion object {
        const val TAG = "MainActivity"
    }

}
