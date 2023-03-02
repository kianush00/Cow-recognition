package com.example.myapplication

class Label {
    var name: String? = null
    var confidence: Double? = null

    override fun toString(): String {
        return "Label(name=$name, confidence=$confidence)"
    }
}