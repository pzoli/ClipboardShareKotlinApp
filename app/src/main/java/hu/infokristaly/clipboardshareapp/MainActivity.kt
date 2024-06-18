package hu.infokristaly.clipboardshareapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import hu.infokristaly.clipboardshareapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.http.NameValuePair
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    var clipDataOnResume: ClipData? = null
    var QRresult: IntentResult? = null
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnScan.setOnClickListener {
            val scanner = IntentIntegrator(this)
            scanner.setOrientationLocked(true)
            scanner.setPrompt("Scan a barcode.")
            scanner.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            scanner.setBarcodeImageEnabled(true)
            scanner.initiateScan()
        }
    }

    fun setClipboardContent(result: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(null, result))
    }

    fun sendContentTask(url: String, clipboardContent: String): Int {
        var result: Int
        try {
            val client: CloseableHttpClient =
                HttpClients.createDefault()
            val httpPost = HttpPost(url)

            val params: MutableList<NameValuePair> = ArrayList()
            params.add(BasicNameValuePair("content", clipboardContent))
            if (url.endsWith("jsonpost")) {
                val json = JSONObject()
                json.put("content", clipboardContent)
                val requestEntity =
                    StringEntity(
                        json.toString(),
                        ContentType.APPLICATION_JSON
                    )
                httpPost.setEntity(requestEntity)
            } else if (url.endsWith("post")) {
                httpPost.setEntity(
                    org.apache.http.client.entity.UrlEncodedFormEntity(
                        params,
                        "UTF-8"
                    )
                )
            }
            val response: CloseableHttpResponse =
                client.execute(httpPost)
            result = response.getStatusLine().getStatusCode()
            client.close()
        } catch (e: Exception) {
            Log.e("MainActivity", e.message.toString())
            result = 500
        } finally {
        }
        return result
    }

    fun getContentTask(url: String): String {
        var result = ""
        try {
            val client = HttpClients.createDefault()
            val httpGost = HttpGet(url)

            val response = client.execute(httpGost)

            val entity = response.entity
            result = EntityUtils.toString(entity, "UTF-8")

            client.close()
        } catch (e: java.lang.Exception) {
            Log.e("MainActivity", e.message.toString())
            result = ""
        } finally {
        }
        return result
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        QRresult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipDataOnResume = manager.primaryClip
            if (clipDataOnResume == null) {
                Toast.makeText(this, "clipDataOnResume is null at focus", Toast.LENGTH_LONG).show()
            } else {
                processQRresult()
            }
        }
    }

    fun processQRresult() {
        if (QRresult != null) {
            if (QRresult!!.getContents() == null) {
                Toast.makeText(this, "Cancelled from fragment", Toast.LENGTH_LONG).show()
            } else {
                if (QRresult!!.getContents().endsWith("post")) {
                    val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    var clipContent = ""
                    if (manager.hasPrimaryClip()) {
                        val clipData = manager.primaryClip
                        val item = clipData!!.getItemAt(0)
                        clipContent = item.text.toString()
                    } else if (clipDataOnResume != null && clipDataOnResume!!.itemCount > 0) {
                        val item = clipDataOnResume!!.getItemAt(0)
                        clipContent = item.text.toString()
                    }

                    if (clipContent == ""){
                        Toast.makeText(this, "clipDataOnResume is null at onActivityResult", Toast.LENGTH_LONG).show()
                    } else {
                        val url = QRresult!!.getContents();
                        Toast.makeText(this, "Location: " + url, Toast.LENGTH_LONG).show()
                        runBlocking {
                            var result: Deferred<Unit> = async() {
                                withContext(Dispatchers.IO) {
                                    val msg = sendContentTask(url, clipContent)
                                }
                            }
                            result.await()
                        }
                    }
                } else if (QRresult!!.getContents().endsWith("get")) {
                    val url = QRresult!!.getContents()
                    Toast.makeText(this, "Location: " + url, Toast.LENGTH_LONG).show()
                    runBlocking {
                        var msg = ""
                        var result: Deferred<Unit> = async() {
                            withContext(Dispatchers.IO) {
                                msg = getContentTask(url)
                            }
                        }
                        result.await()
                        setClipboardContent(msg)
                    }
                }
            }
        }
    }
}

