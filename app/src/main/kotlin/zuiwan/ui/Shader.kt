package zuiwan.ui

import android.annotation.SuppressLint
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import zuiwan.model.ShaderPreset
import zuiwan.model.SpectrumFrame
import zuiwan.model.VisualMode
import zuiwan.theme.LocalGlass
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun mediabackdrop(
    wallpaperPath: String,
    motionPath: String,
    motionIsVideo: Boolean,
    blurRadius: Float,
    bgDim: Float,
    modifier: Modifier = Modifier,
) {
    val glassTokens = LocalGlass.current
    val motionFile = remember(motionPath) {
        motionPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
    }
    val wallFile = remember(wallpaperPath) {
        wallpaperPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
    }
    if (motionFile == null && wallFile == null) return

    val blurMod = if (blurRadius > 1f) Modifier.blur(blurRadius.dp) else Modifier

    Box(modifier = modifier.fillMaxSize()) {
        when {
            motionFile != null && motionIsVideo -> {
                videolayer(
                    file = motionFile,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(blurMod),
                )
            }
            motionFile != null && motionFile.extension.equals("gif", ignoreCase = true) -> {
                giflayer(
                    file = motionFile,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(blurMod),
                )
            }
            motionFile != null -> {
                AsyncImage(
                    model = motionFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(blurMod),
                )
            }
            wallFile != null -> {
                AsyncImage(
                    model = wallFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(blurMod),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(glassTokens.bg.copy(alpha = bgDim.coerceIn(0.08f, 0.94f))),
        )
    }
}

@Composable
private fun giflayer(
    file: File,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { view ->
                if (view.tag != file.absolutePath) {
                    view.tag = file.absolutePath
                    runCatching {
                        val src = ImageDecoder.createSource(file)
                        val drawable = ImageDecoder.decodeDrawable(src)
                        view.setImageDrawable(drawable)
                        if (drawable is AnimatedImageDrawable) {
                            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                            drawable.start()
                        }
                    }
                }
            },
        )
    } else {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

@Composable
private fun videolayer(
    file: File,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(lifecycleOwner, mediaPlayer) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { mediaPlayer?.start() }
                Lifecycle.Event.ON_PAUSE -> runCatching { mediaPlayer?.pause() }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
        }
    }

    DisposableEffect(file.absolutePath) {
        onDispose {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            mediaPlayer = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { texView ->
            if (texView.tag != file.absolutePath) {
                texView.tag = file.absolutePath
                fun bindplayer(st: SurfaceTexture, vw: Int, vh: Int) {
                    runCatching {
                        mediaPlayer?.release()
                        val mp = MediaPlayer()
                        mp.setSurface(Surface(st))
                        mp.setDataSource(file.absolutePath)
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                        mp.setOnVideoSizeChangedListener { _, videoW, videoH ->
                            scalecrop(texView, vw, vh, videoW, videoH)
                        }
                        mp.setOnPreparedListener { prepared ->
                            prepared.setVolume(0f, 0f)
                            prepared.start()
                        }
                        mp.prepareAsync()
                        mediaPlayer = mp
                    }
                }
                if (texView.isAvailable && texView.surfaceTexture != null) {
                    bindplayer(texView.surfaceTexture!!, texView.width, texView.height)
                }
                texView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        bindplayer(st, w, h)
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        val mp = mediaPlayer ?: return
                        scalecrop(texView, w, h, mp.videoWidth, mp.videoHeight)
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        runCatching {
                            mediaPlayer?.stop()
                            mediaPlayer?.release()
                        }
                        mediaPlayer = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
    )
}

private fun scalecrop(view: TextureView, viewW: Int, viewH: Int, videoW: Int, videoH: Int) {
    if (viewW <= 0 || viewH <= 0 || videoW <= 0 || videoH <= 0) return
    val sx = viewW.toFloat() / videoW.toFloat()
    val sy = viewH.toFloat() / videoH.toFloat()
    val maxScale = maxOf(sx, sy)
    val matrix = Matrix()
    matrix.setScale(maxScale / sx, maxScale / sy, viewW / 2f, viewH / 2f)
    view.setTransform(matrix)
}

@Composable
fun reactivevisual(
    mode: VisualMode,
    preset: ShaderPreset,
    frame: SpectrumFrame,
    customCss: String = "",
    customJs: String = "",
    modifier: Modifier = Modifier,
) {
    val glassTokens = LocalGlass.current
    when (mode) {
        VisualMode.SHADER -> shaderstage(
            preset = preset,
            frame = frame,
            accent = glassTokens.accent,
            secondary = glassTokens.accentSecondary,
            customCss = customCss,
            customJs = customJs,
            modifier = modifier,
        )
        VisualMode.BARS -> spectrumbars(
            frame = frame,
            accent = glassTokens.accent,
            secondary = glassTokens.accentSecondary,
            modifier = modifier,
        )
        VisualMode.ORB -> reactiveorb(
            frame = frame,
            accent = glassTokens.accent,
            secondary = glassTokens.accentSecondary,
            modifier = modifier,
        )
        VisualMode.RIBBON -> waveribbon(
            frame = frame,
            accent = glassTokens.accent,
            secondary = glassTokens.accentSecondary,
            modifier = modifier,
        )
    }
}

@Composable
private fun spectrumbars(
    frame: SpectrumFrame,
    accent: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val count = frame.bands.size.coerceAtLeast(1)
        val totalW = size.width * 0.88f
        val startX = (size.width - totalW) * 0.5f
        val slotW = totalW / count
        val barW = (slotW * 0.64f).coerceAtLeast(3f)
        val centerY = size.height * 0.54f
        val maxHalf = size.height * 0.36f
        val brush = Brush.verticalGradient(listOf(secondary, accent, secondary))

        for (i in 0 until count) {
            val v = frame.bands[i].coerceIn(0.03f, 1f)
            val halfH = maxHalf * v * (1f + frame.beat * 0.18f)
            val x = startX + i * slotW + (slotW - barW) * 0.5f
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, centerY - halfH),
                size = Size(barW, halfH * 2f),
                cornerRadius = CornerRadius(barW * 0.5f, barW * 0.5f),
                alpha = (0.45f + v * 0.55f).coerceIn(0f, 1f),
            )
        }
    }
}

@Composable
private fun reactiveorb(
    frame: SpectrumFrame,
    accent: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width * 0.5f, size.height * 0.48f)
        val baseR = min(size.width, size.height) * 0.24f
        val pulseR = baseR * (1f + frame.bass * 0.42f + frame.beat * 0.24f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = (0.38f + frame.beat * 0.3f).coerceAtMost(0.75f)),
                    secondary.copy(alpha = 0.16f),
                    Color.Transparent,
                ),
                center = center,
                radius = pulseR * 1.95f,
            ),
            radius = pulseR * 1.95f,
            center = center,
        )

        val spokes = frame.bands.size
        val path = Path()
        for (i in 0 until spokes) {
            val angle = (2.0 * PI * i / spokes).toFloat()
            val amp = frame.bands[i]
            val r = pulseR * (0.85f + amp * 0.58f)
            val px = center.x + cos(angle) * r
            val py = center.y + sin(angle) * r
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        drawPath(
            path = path,
            brush = Brush.sweepGradient(listOf(accent, secondary, accent), center),
            style = Stroke(width = 5f, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun waveribbon(
    frame: SpectrumFrame,
    accent: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val pts = frame.wave
        if (pts.size < 2) return@Canvas
        val midY = size.height * 0.52f
        val ampScale = size.height * (0.20f + frame.bass * 0.16f)

        for (layer in 0..2) {
            val path = Path()
            val layerFactor = 1f - layer * 0.26f
            pts.forEachIndexed { idx, sample ->
                val x = size.width * (idx.toFloat() / (pts.size - 1))
                val y = midY + sample * ampScale * layerFactor
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = if (layer % 2 == 0) accent.copy(alpha = 0.85f - layer * 0.25f) else secondary.copy(alpha = 0.7f),
                style = Stroke(width = (6f - layer * 1.5f).coerceAtLeast(2f), cap = StrokeCap.Round),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun shaderstage(
    preset: ShaderPreset,
    frame: SpectrumFrame,
    accent: Color,
    secondary: Color,
    customCss: String = "",
    customJs: String = "",
    modifier: Modifier = Modifier,
) {
    var webRef by remember { mutableStateOf<WebView?>(null) }
    val presetIdx = preset.ordinal

    val htmlPayload = remember(presetIdx, customCss, customJs) {
        buildshader(presetIdx, customCss, customJs)
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webRef?.loadUrl("about:blank")
                webRef?.destroy()
            }
            webRef = null
        }
    }

    LaunchedEffect(frame.stamp, accent, secondary, presetIdx) {
        val view = webRef ?: return@LaunchedEffect
        val jsCall = String.format(
            Locale.US,
            "window.u&&window.u(%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f);",
            frame.bass,
            frame.mid,
            frame.treble,
            frame.beat,
            accent.red,
            accent.green,
            accent.blue,
            secondary.red,
            secondary.green,
            secondary.blue,
        )
        view.evaluateJavascript(jsCall, null)
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                webRef = this
            }
        },
        update = { view ->
            if (view.tag != htmlPayload) {
                view.tag = htmlPayload
                view.loadDataWithBaseURL("https://zuiwan.local/", htmlPayload, "text/html", "utf-8", null)
            }
        },
    )
}

private fun buildshader(mode: Int, customCss: String, customJs: String): String =
    """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<style>
html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:#05060a;}
canvas{width:100%;height:100%;display:block;}
$customCss
</style>
</head>
<body>
<canvas id="gl"></canvas>
<script>
const c=document.getElementById('gl');
const gl=c.getContext('webgl',{antialias:false,depth:false,stencil:false,powerPreference:'high-performance'});
let b=0.1,m=0.1,tr=0.1,bt=0.0,ar=0.48,ag=0.72,ab=1.0,sr=0.65,sg=0.54,sb=0.98;
window.u=function(nb,nm,nt,nbt,r1,g1,b1,r2,g2,b2){b=nb;m=nm;tr=nt;bt=nbt;ar=r1;ag=g1;ab=b1;sr=r2;sg=g2;sb=b2;};
function rs(){const d=Math.min(window.devicePixelRatio||1,1.5);c.width=innerWidth*d;c.height=innerHeight*d;if(gl)gl.viewport(0,0,c.width,c.height);}
addEventListener('resize',rs);rs();
if(gl){
const vs='attribute vec2 p;void main(){gl_Position=vec4(p,0.0,1.0);}';
const fs=`precision mediump float;
uniform vec2 R;uniform float T,B,M,TR,BT;uniform vec3 C1,C2;
float h(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float n(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(h(i),h(i+vec2(1,0)),f.x),mix(h(i+vec2(0,1)),h(i+vec2(1,1)),f.x),f.y);}
float fbm(vec2 p){float v=0.0,a=0.5;for(int i=0;i<5;i++){v+=a*n(p);p*=2.03;a*=0.5;}return v;}
void main(){
  vec2 uv=(gl_FragCoord.xy-0.5*R.xy)/min(R.x,R.y);
  float t=T*(0.45+B*0.55);
  vec3 col=vec3(0.03,0.04,0.07);
  int md=${mode};
  if(md==0){
    vec2 q=vec2(fbm(uv*2.2+t*0.3),fbm(uv*2.2-t*0.25));
    float f=fbm(uv*2.6+q*(1.5+B*1.8)+vec2(0.0,t*0.2));
    col=mix(vec3(0.02,0.03,0.06),C1,clamp(f*1.35,0.0,1.0));
    col=mix(col,C2,clamp(length(q)*(0.7+M),0.0,1.0));
    col+=C1*BT*0.28/(0.35+length(uv));
  }else if(md==1){
    float r=length(uv);float a=atan(uv.y,uv.x);
    float f=fbm(vec2(r*3.0-t*0.6,a*1.8+fbm(uv*3.0)));
    col=mix(vec3(0.02,0.02,0.05),C2,f*(0.9+B));
    col+=C1*pow(max(0.0,1.0-r*(1.1-BT*0.35)),2.2)*(0.7+M);
  }else if(md==2){
    float d=length(uv);
    float w=sin(d*(18.0-B*6.0)-T*(3.2+BT*2.0))*0.5+0.5;
    float ring=smoothstep(0.75,1.0,w)/(0.25+d*1.8);
    col=mix(vec3(0.02,0.03,0.06),mix(C1,C2,sin(d*4.0-T)*0.5+0.5),ring*(0.8+B*1.1));
  }else if(md==3){
    vec2 g=uv;g.y+=0.15;
    float hz=smoothstep(0.06,-0.02,abs(g.y));
    vec2 p=vec2(g.x/(abs(g.y)+0.08),1.0/(abs(g.y)+0.08)+T*(1.8+B*2.0));
    float ln=max(smoothstep(0.92,1.0,cos(p.x*3.14159)),smoothstep(0.92,1.0,cos(p.y*3.14159)));
    col=mix(vec3(0.03,0.03,0.07),C1,ln*clamp(-g.y*2.2,0.0,1.0)*(0.6+B));
    col+=C2*hz*(0.7+BT*0.7);
  }else{
    vec2 w=uv;
    for(int i=1;i<5;i++){float fi=float(i);w.x+=0.32/fi*sin(fi*2.5*w.y+t+B*1.6);w.y+=0.32/fi*cos(fi*2.5*w.x+t+M*1.6);}
    float v=0.5+0.5*sin(w.x*2.4+w.y*2.4);
    col=mix(C1*0.22,mix(C1,C2,v),0.55+0.45*cos(length(w)*2.0-t));
    col+=BT*0.2*C2;
  }
  gl_FragColor=vec4(col,1.0);
}`;
function sh(tp,sc){const s=gl.createShader(tp);gl.shaderSource(s,sc);gl.compileShader(s);return s;}
const pg=gl.createProgram();gl.attachShader(pg,sh(gl.VERTEX_SHADER,vs));gl.attachShader(pg,sh(gl.FRAGMENT_SHADER,fs));gl.linkProgram(pg);gl.useProgram(pg);
const bf=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,bf);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,3,-1,-1,3]),gl.STATIC_DRAW);
const pl=gl.getAttribLocation(pg,'p');gl.enableVertexAttribArray(pl);gl.vertexAttribPointer(pl,2,gl.FLOAT,false,0,0);
const uR=gl.getUniformLocation(pg,'R'),uT=gl.getUniformLocation(pg,'T'),uB=gl.getUniformLocation(pg,'B'),uM=gl.getUniformLocation(pg,'M'),uTR=gl.getUniformLocation(pg,'TR'),uBT=gl.getUniformLocation(pg,'BT'),uC1=gl.getUniformLocation(pg,'C1'),uC2=gl.getUniformLocation(pg,'C2');
let s0=performance.now();
function loop(now){
  gl.uniform2f(uR,c.width,c.height);
  gl.uniform1f(uT,(now-s0)*0.001);
  gl.uniform1f(uB,b);gl.uniform1f(uM,m);gl.uniform1f(uTR,tr);gl.uniform1f(uBT,bt);
  gl.uniform3f(uC1,ar,ag,ab);gl.uniform3f(uC2,sr,sg,sb);
  gl.drawArrays(gl.TRIANGLES,0,3);
  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);
}
try{ $customJs }catch(e){}
</script>
</body>
</html>
    """.trimIndent()
