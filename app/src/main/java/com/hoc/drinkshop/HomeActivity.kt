package com.hoc.drinkshop

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.support.design.widget.NavigationView
import android.support.design.widget.Snackbar
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.daimajia.slider.library.SliderTypes.BaseSliderView
import com.daimajia.slider.library.SliderTypes.TextSliderView
import com.facebook.accountkit.AccountKit
import com.hoc.drinkshop.MainActivity.Companion.USER
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_home.*
import kotlinx.android.synthetic.main.app_bar_home.*
import kotlinx.android.synthetic.main.content_home.*
import kotlinx.android.synthetic.main.nav_header_home.view.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.jetbrains.anko.*
import org.koin.android.ext.android.inject
import retrofit2.Retrofit
import java.io.ByteArrayOutputStream

fun Bitmap.getResizedBitmap(newWidth: Int, newHeight: Int, isNecessaryToKeepOrig: Boolean): Bitmap {
    val matrix = Matrix().apply {
        postScale(newWidth.toFloat() / width, newHeight.toFloat() / height)
    }
    val resizedBitmap = Bitmap.createBitmap(this, 0, 0, width, height, matrix, false)
    if (!isNecessaryToKeepOrig) {
        recycle()
    }
    return resizedBitmap
}

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
        startActivity<DrinkActivity>(CATEGORY to category, MainActivity.USER to user)
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

        user = intent.getParcelableExtra(USER)
        nav_view.getHeaderView(0).apply {
            headerView = this
            bindHeaderView()
            imageViewAvatar.setOnClickListener { selectImage() }
        }

        recyclerCategory.run {
            layoutManager = GridLayoutManager(this@HomeActivity, 2)
            setHasFixedSize(true)
            adapter = categoryAdapter
        }

        getData()
        swipeLayout.setOnRefreshListener(::getData)
    }

    private fun getData() {
        val getBanners = async(parent = parentJob) { apiService.getBanners().await() }
        val getAllCategories = async(parent = parentJob) { apiService.getAllCategories().await() }
        val getUser = async(parent = parentJob) { apiService.getUserByPhone(user.phone).await() }

        launch(UI, parent = parentJob) {
            try {
                info("start getData")
                val banners = getBanners.await()
                info("1")
                val categories = getAllCategories.await()
                info("2")
                val user = getUser.await()
                info("3")
                swipeLayout.post { swipeLayout.isRefreshing = false }

                info("${banners.size} ${categories.size} $user")
                //update slider layout
                sliderLayout.removeAllSliders()
                banners.forEach { (_, name, imageUrl) ->
                    TextSliderView(this@HomeActivity)
                            .description(name)
                            .image(imageUrl)
                            .setScaleType(BaseSliderView.ScaleType.Fit)
                            .let(sliderLayout::addSlider)
                }

                //update recycler categories
                categoryAdapter.submitList(categories)

                //update navigation view
                this@HomeActivity.user = user.also { info("User: $user") }
                bindHeaderView()
            } catch (exception: Throwable) {

            }
        }
    }

    private fun bindHeaderView() = headerView.run {
        info("Bind view: $user")
        textUserName.text = user.name
        textUserPhone.text = user.phone

        val imageUrl = user.imageUrl
        toast("ImageUrl: $BASE_URL$imageUrl")
        if (!imageUrl.isNullOrEmpty()) {
            Picasso.with(context)
                    .load("$BASE_URL$imageUrl")
                    .fit()
                    .error(R.drawable.ic_account_circle_black_24dp)
                    .placeholder(R.drawable.ic_account_circle_black_24dp)
                    .into(imageViewAvatar)
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
                Picasso.with(this)
                        .load(imageUri)
                        .placeholder(R.drawable.ic_image_black_24dp)
                        .placeholder(R.drawable.ic_image_black_24dp)
                        .into(headerView.imageViewAvatar)
                uploadImage(imageUri, user.phone)
            }
        }
    }

    private fun uploadImage(imageUri: Uri?, phone: String) {
        if (imageUri == null) return

        launch(UI, parent = parentJob) {
            val fileName = contentResolver.query(imageUri, null, null, null, null)
                    .use {
                        it.moveToFirst()
                        it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                    }

            val bytes = ByteArrayOutputStream().use {
                val width = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        72f,
                        resources.displayMetrics
                ).toInt()
                MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                        .getResizedBitmap(
                                width,
                                width,
                                false
                        ).compress(Bitmap.CompressFormat.PNG, 100, it)
                it.toByteArray()
            }
            val contentType = MediaType.parse(contentResolver.getType(imageUri))
            val requestFile: RequestBody = RequestBody.create(contentType, bytes)

            val body: MultipartBody.Part = MultipartBody.Part.createFormData("image", fileName, requestFile)
            apiService.uploadImage(body, phone)
                    .awaitResult()
                    .onSuccess {
                        toast("Upload image successfully")
                        user = it
                        bindHeaderView()
                    }
                    .onException {
                        toast("Cannot upload image because ${it.message ?: "unknown error"}")
                    }
                    .onError {
                        toast("Cannot upload image because ${retrofit.parseResultErrorMessage(it.first)}")
                    }
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
                        startActivity(intentFor<MainActivity>().clearTask().newTask())
                        finish()
                    }
                    negativeButton("Cancel") {
                        it.dismiss()
                    }
                }.show()
            }
            R.id.nav_fav_drink -> {
                startActivity<FavoritesActivity>(USER to user)
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
