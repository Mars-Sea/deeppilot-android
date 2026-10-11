package dev.hailab.deeppilot

import android.app.Application
import android.content.Context

class DeepPilotApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.start()
    }
}

val Context.graph: AppGraph get() = (applicationContext as DeepPilotApp).graph
