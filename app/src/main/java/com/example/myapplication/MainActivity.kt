package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var btnTakePhoto: Button
    private lateinit var btnPickMedia: Button
    private var labelArray = mutableListOf<Label>()

    //Evento que procesa el resultado de la camara y envia la vista previa de la foto al ImageView
    private val takePhoto =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                try {
                    val imageBitmap = getImageBitmapFromCamera(result)
                    setImageView(imageBitmap)
                    uploadImageToS3Bucket(imageBitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                    showPopupMessage(getString(R.string.notify_something_went_wrong))
                }
            }
        }

    //Evento que selecciona una imagen de la galeria y envia la vista previa de la foto al ImageView
    private val pickMedia =
        registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
        // Callback is invoked after the user selects a media item or closes the photo picker.
        if (uri != null) {
            try {
                Log.d("PhotoPicker", "Selected URI: $uri")
                val imageBitmap = getImageBitmapFromGallery(uri)
                setImageView(imageBitmap)
                uploadImageToS3Bucket(imageBitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                showPopupMessage(getString(R.string.notify_something_went_wrong))
            }
        } else {
            Log.d("PhotoPicker", "No media selected")
            showPopupMessage(getString(R.string.notify_no_media_selected))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnTakePhoto = findViewById(R.id.btnCamara)
        btnPickMedia = findViewById(R.id.btnPickMedia)

        //Evento al presionar el boton "Tomar foto"
        btnTakePhoto.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED
                        || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED)
            ) {
                val permissions = arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                requestPermissions(permissions, Constants.CAMERA_PERMISSION_REQUEST_CODE)
            } else {
                takePhoto()
            }
        }

        //Evento al presionar el boton "Ir a galeria"
        btnPickMedia.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
            ) {
                val permissions = arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
                requestPermissions(permissions, Constants.GALLERY_PERMISSION_REQUEST_CODE)
            } else {
                selectFromGallery()
            }
        }
    }

    private fun takePhoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePhoto.launch(intent)
    }

    private fun selectFromGallery() {
        val pickVisualMediaRequest = PickVisualMediaRequest(
            ActivityResultContracts.PickVisualMedia.ImageOnly)
        pickMedia.launch(pickVisualMediaRequest)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == Constants.CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                takePhoto()
            } else {
                showPopupMessage(getString(R.string.notify_permission_denied))
            }
        }

        if (requestCode == Constants.GALLERY_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                selectFromGallery()
            } else {
                showPopupMessage(getString(R.string.notify_permission_denied))
            }
        }
    }

    private fun uploadImageToS3Bucket(bitmap: Bitmap) {
        val credentials = BasicAWSCredentials(Constants.ACCESS_ID, Constants.SECRET_KEY)
        val s3Client = AmazonS3Client(credentials, Region.getRegion(Regions.US_EAST_1))

        val transferUtility = TransferUtility.builder()
            .context(this)
            .s3Client(s3Client)
            .build()

        val file = createImageFile(bitmap)
        val imageKey = Utils.getRandomString()
        val uploadObserver = transferUtility.upload(Constants.BUCKET_NAME,
            imageKey + Constants.JPG_SUFFIX, file)

        uploadObserver.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState) {
                when (state) {
                    TransferState.COMPLETED -> {
                        runOnUiThread {
                            showPopupMessage(getString(R.string.notify_waiting_response))
                        }
                        Log.d("msg", "TransferState: COMPLETED")
                        sendGetRequestOnNewThread(imageKey)
                    }
                    TransferState.FAILED -> {
                        Log.d("msg", "TransferState: FAILED")
                    }
                    TransferState.WAITING_FOR_NETWORK -> {
                        Log.d("msg", "TransferState: WAITING_FOR_NETWORK")
                    }
                    else -> {}
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                val percentDoneF = bytesCurrent.toFloat() / bytesTotal.toFloat() * 100
                val percentDone = percentDoneF.toInt()
                Log.d("TAG", "Upload: $percentDone%")
            }

            override fun onError(id: Int, ex: Exception) {
                ex.printStackTrace()
            }
        })
    }

    private fun sendGetRequestOnNewThread(imageKey: String = "") {
        val thread = Thread {
            try {
                sendGetRequestAndInitializeLabels(imageKey)
                changeTextView()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        thread.start()
    }

    @SuppressLint("SetTextI18n")
    private fun sendGetRequestAndInitializeLabels(imageKey: String = "") {
        val reqParam = Utils.getReqParam(imageKey)
        val mURL = URL(Constants.URL + reqParam)

        with(mURL.openConnection() as HttpURLConnection) {
            // optional default is GET
            requestMethod = "GET"

            println("URL : $url")
            println("Response Code : $responseCode")

            if (responseCode in 500..599) {
                runOnUiThread {
                    showPopupMessage(getString(R.string.notify_cannot_connect_to_server))
                    findViewById<TextView>(R.id.tvCowType).text = "No se pudo determinar el tipo de vaca"
                }
            } else if (responseCode == 200) {
                val strResponse: String

                BufferedReader(InputStreamReader(inputStream)).use {
                    val response = StringBuffer()

                    var inputLine = it.readLine()
                    while (inputLine != null) {
                        response.append(inputLine)
                        inputLine = it.readLine()
                    }
                    it.close()
                    println("Response : $response")
                    strResponse = response.toString().trimIndent()
                }

                initializeLabelArray(strResponse)
            }
        }
    }

    private fun initializeLabelArray(response: String) {
        labelArray.clear()

        val labelJSONArray = Utils.getJSONArray(response)
        for (i in 0 until(labelJSONArray.length())){
            val theLabel = initializeLabelObject(labelJSONArray, i)
            labelArray.add(theLabel)
        }

        println("Label array: $labelArray")
    }

    private fun initializeLabelObject(labelJSONArray : JSONArray, i: Int) : Label {
        val theLabel = Label()

        val name = labelJSONArray
            .getJSONObject(i)
            .getString("name")
        theLabel.name = name

        val confidence = labelJSONArray
            .getJSONObject(i)
            .getString("confidence").toDouble()
        theLabel.confidence = confidence

        return theLabel
    }

    @SuppressLint("SetTextI18n")
    private fun changeTextView() {
        runOnUiThread {
            if (labelArray.isNotEmpty()) {
                if (labelArray[0].name == "not-a-cow") {
                    findViewById<TextView>(R.id.tvCowType).text = "No es una vaca"
                } else {
                    run breaking@ {
                        labelArray.forEach { label ->
                            if (label.name in LabelHandler.cowTypeLabelArray) {
                                findViewById<TextView>(R.id.tvCowType).text =
                                    LabelHandler.getCowTypeLabelToText(label.name)
                                return@breaking
                            }
                        }
                    }

                    run breaking@ {
                        labelArray.forEach { label ->
                            if (label.name in LabelHandler.cowSizeLabelArray) {
                                findViewById<TextView>(R.id.tvCowSize).text =
                                    LabelHandler.getCowSizeLabelToText(label.name)
                                return@breaking
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createImageFile(bitmap: Bitmap) : File {
        val file = File.createTempFile("image_", Constants.JPG_SUFFIX, applicationContext.cacheDir)
        file.deleteOnExit()
        val outputStream = file.outputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.close()

        return file
    }

    private fun setImageView(imageBitmap: Bitmap) {
        val imageView = findViewById<ImageView>(R.id.imageView)
        imageView.setImageBitmap(imageBitmap)
    }

    private fun getImageBitmapFromCamera(result: ActivityResult) : Bitmap {
        return result.data?.extras?.get("data") as Bitmap
    }

    private fun getImageBitmapFromGallery(uri: Uri) : Bitmap {
        val imageStream = contentResolver.openInputStream(uri)
        return BitmapFactory.decodeStream(imageStream)
    }

    private fun showPopupMessage(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }
}
