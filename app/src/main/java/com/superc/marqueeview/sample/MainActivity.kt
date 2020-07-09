package com.superc.marqueeview.sample

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.superc.marqueeview.MarqueeOnItemClickListener
import com.superc.marqueeview.SimpleMF
import com.superc.marqueeview.SimpleMarqueeView
import kotlinx.android.synthetic.main.activity_main.*

@Suppress("UNCHECKED_CAST")
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val arr = arrayOf(
            "于是，我在想，能不能开发出一款支持对 View 进行复用",
            "内部对 View 进行复用，有多少种 type，内部就有多少个 View。",
            "首先，若 MarqueeView 的 ViewType 只有一种类型，那么只需要继承 CommonAdapter 即可首先，若 MarqueeView 的 ViewType 只有一种类型，那么只需要继承 CommonAdapter 即可首先，若 MarqueeView 的 ViewType 只有一种类型，那么只需要继承 CommonAdapter 即可"
        )
        val mf: SimpleMF<String> = SimpleMF(this)
        mf.setData(mutableListOf(*arr))
        val sim: SimpleMarqueeView<String> = simpleMarqueeView as SimpleMarqueeView<String>
        sim.setMarqueeFactory(mf)
        sim.postDelayed({ sim.startFlipping() }, 300)
        sim.setOnItemClickListener(object : MarqueeOnItemClickListener<TextView, String> {
            override fun onItemClickListener(mView: TextView, mData: String, mPosition: Int) {
                Toast.makeText(applicationContext, "$mData $mPosition", Toast.LENGTH_LONG)
                    .show()
            }
        })
    }
}
