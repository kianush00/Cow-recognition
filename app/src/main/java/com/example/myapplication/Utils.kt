package com.example.myapplication

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object Utils {
    fun getRandomString(length: Int = 24): String {
        val charset = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..length)
            .map { charset.random() }
            .joinToString("")
    }

    fun getReqParam(imageKey: String = ""): String {
        return "?" + URLEncoder.encode("q", "UTF-8") + "=" +
                URLEncoder.encode(imageKey + Constants.JPG_SUFFIX, "UTF-8")
    }

    fun getJSONArray(response: String) : JSONArray {
        val labelJsonObject = JSONObject(response)
        return labelJsonObject.getJSONArray("labels")
    }
}