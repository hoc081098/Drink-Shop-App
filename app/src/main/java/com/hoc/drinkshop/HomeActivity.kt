package com.hoc.drinkshop

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.OpenableColumns
import android.support.design.widget.NavigationView
import android.support.design.widget.Snackbar
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.daimajia.slider.library.SliderTypes.BaseSliderView
import com.daimajia.slider.library.SliderTypes.TextSliderView
import com.facebook.accountkit.AccountKit
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_home.*
import kotlinx.android.synthetic.main.app_bar_home.*
import kotlinx.android.synthetic.main.content_home.*
import kotlinx.android.synthetic.main.nav_header_home.view.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.alert
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.toast
import org.koin.android.ext.android.inject
import retrofit2.Call
import retrofit2.Retrofit
import java.io.ByteArrayOutputStream

class HomeActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, AnkoLogger {
    override val loggerTag: String = "MY_TAG_HOME"

    private val apiService by inject<ApiService>()
    private val retrofit by inject<Retrofit>()
    private val parentJob = Job()
    private val categoryAdapter = CategoryAdapter(::navigateToDrinkActivity)
    private var doubleBackToExist = false
    private var imageUri: Uri? = null
    private lateinit var user: User
    private lateinit var headerView: View

    private fun navigateToDrinkActivity(category: Category) {
        startActivity<DrinkActivity>(CATEGORY to category)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()
        nav_view.setNavigationItemSelectedListener(this)

        user = intent.getParcelableExtra<User>(MainActivity.USER)
        nav_view.getHeaderView(0).apply {
            headerView = this
            bindHeaderView(this)
            imageViewAvatar.setOnClickListener { selectImage() }
        }

        recyclerCategory.run {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
            adapter = categoryAdapter
        }

        getAndShowBannerSlider()
        getCategories()

        swipeLayout.setOnRefreshListener {
            getAndShowBannerSlider()
            getCategories()
            getUserInfomation()
        }
    }

    private fun bindHeaderView(headerView: View) = headerView.run {
        textUserName.text = user.name
        textUserPhone.text = user.phone
        user.imageUrl?.let {
            Picasso.with(this@HomeActivity)
                    .load(it)
                    .placeholder(R.drawable.ic_image_black_24dp)
                    .placeholder(R.drawable.ic_image_black_24dp)
                    .into(imageViewAvatar)
        }
    }

    private fun getUserInfomation() {
        launch(UI, parent = parentJob) {
            apiService.getUserInfomation(user.phone)
                    .awaitResult()
                    .onSuccess {
                        user = it
                        bindHeaderView(headerView)
                    }
        }
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(Intent.createChooser(intent, "Select an image"), SELECT_IMAGE_RC)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            SELECT_IMAGE_RC -> if (resultCode == Activity.RESULT_OK) {
                imageUri = data?.data
                Picasso.with(this@HomeActivity)
                        .load(imageUri)
                        .placeholder(R.drawable.ic_image_black_24dp)
                        .placeholder(R.drawable.ic_image_black_24dp)
                        .into(headerView.imageViewAvatar)
                uploadImage(imageUri, user.phone)
            }
        }
    }

    private fun uploadImage(imageUri: Uri?, phone: String) {
        if (imageUri == null) {
            toast("Please select an image")
            return
        }

        launch(UI, parent = parentJob) {
            val fileName = contentResolver.query(imageUri, null, null, null, null).use {
                it.moveToFirst()
                it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }

            val bytes = ByteArrayOutputStream().use {
                contentResolver.openInputStream(imageUri).copyTo(it)
                it.toByteArray()
            }
            val contentType = MediaType.parse(contentResolver.getType(imageUri))
            val requestFile: RequestBody = RequestBody.create(contentType, bytes)

            val body: MultipartBody.Part = MultipartBody.Part.createFormData("image", fileName, requestFile)
            apiService.uploadImage(body, phone).awaitResult()
                    .onSuccess {
                        user = user.copy(imageUrl = it.imageUri)
                        toast(it.message)
                    }
                    .onException {
                        toast("Cannot upload image because ${it.message ?: "unknown error"}")
                    }
                    .onError {
                        toast("Cannot upload image because ${retrofit.parse<Error>(it).message}")
                    }
        }
    }

    private fun getCategories() {
        getDataFromService(
                { apiService.getAllCategories() },
                { categoryAdapter.submitList(it) },
                { toast("Cannot get categories because $it") }
        )
    }

    private fun getAndShowBannerSlider() {
        getDataFromService(
                { apiService.getBanners(3) },
                {
                    it.forEach { (name, imageUrl) ->
                        TextSliderView(this)
                                .description(name)
                                .image(imageUrl)
                                .setScaleType(BaseSliderView.ScaleType.Fit)
                                .let(sliderLayout::addSlider)
                    }
                },
                { toast("Cannot get banners because $it") }
        )
    }

    private inline fun <T : Any> getDataFromService(
            crossinline getData: () -> Call<T>,
            crossinline onSuccess: (T) -> Unit,
            crossinline onErrorOrException: (String) -> Unit
    ) {
        launch(UI, parent = parentJob) {
            getData().awaitResult()
                    .onSuccess { onSuccess(it) }
                    .onError { onErrorOrException(retrofit.parse<Error>(it).message) }
                    .onException { onErrorOrException(it.message ?: "unknown error") }
            swipeLayout.post { swipeLayout.isRefreshing = false }
        }
    }

    override fun onStart() {
        super.onStart()
        sliderLayout.startAutoCycle()
    }

    override fun onStop() {
        super.onStop()
        sliderLayout.stopAutoCycle()
    }

    override fun onDestroy() {
        super.onDestroy()
        parentJob.cancel()
    }

    override fun onBackPressed() {
        when {
            drawer_layout.isDrawerOpen(GravityCompat.START) -> drawer_layout.closeDrawer(GravityCompat.START)
            doubleBackToExist -> super.onBackPressed()
            else -> {
                doubleBackToExist = true
                toast("Back pressed again to exit")
                Handler().postDelayed({ doubleBackToExist = false }, 2_000)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.action_settings -> return true
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_sign_out -> {
                alert(message = "Are you sure you want to log out", title = "LOG OUT") {
                    positiveButton("Ok") {
                        it.dismiss()
                        AccountKit.logOut()
                    }
                    negativeButton("Cancel") {
                        it.dismiss()
                    }
                }.show()
            }
        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    companion object {
        const val CATEGORY = "category"
        const val SELECT_IMAGE_RC = 1
    }
}
