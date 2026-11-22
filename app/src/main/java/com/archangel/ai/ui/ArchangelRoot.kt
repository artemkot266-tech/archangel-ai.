package com.archangel.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archangel.ai.ArchangelApp
import com.archangel.ai.AssistantViewModel
import com.archangel.ai.MarketViewModel
import com.archangel.ai.PendingAction
import com.archangel.ai.core.CoreState
import com.archangel.ai.phone.AppScreen
import com.archangel.ai.phone.PhoneAction
import com.archangel.ai.ui.components.AmbientBackground
import com.archangel.ai.ui.components.MonoLabel
import com.archangel.ai.ui.components.Pill
import com.archangel.ai.ui.screens.ChatScreen
import com.archangel.ai.ui.screens.CoreScreen
import com.archangel.ai.ui.screens.MarketScreen
import com.archangel.ai.ui.screens.OnboardingScreen
import com.archangel.ai.ui.screens.TasksScreen
import com.archangel.ai.features.BriefingScheduler
import com.archangel.ai.features.Weather
import com.archangel.ai.ui.screens.SettingsScreen
import com.archangel.ai.ui.screens.lastAssistantReply
import com.archangel.ai.ui.theme.Ash
import com.archangel.ai.ui.theme.Bone
import com.archangel.ai.ui.theme.Ember
import com.archangel.ai.ui.theme.Hairline
import com.archangel.ai.ui.theme.Obsidian
import com.archangel.ai.ui.theme.Smoke
import com.archangel.ai.ui.theme.Surface1
import com.archangel.ai.ui.theme.TextStyles

/** Что открыть при запуске из ярлыка. */
enum class LaunchAction {
    VOICE, MARKET, BRIEFING;

    companion object {
        fun from(intent: android.content.Intent?): LaunchAction? = when (intent?.action) {
            "com.archangel.ai.action.VOICE" -> VOICE
            "com.archangel.ai.action.MARKET" -> MARKET
            "com.archangel.ai.action.BRIEFING" -> BRIEFING
            else -> null
        }
    }
}

@Composable
fun ArchangelRoot(
    launchAction: LaunchAction? = null,
    onLaunchActionHandled: () -> Unit = {},
    sharedText: String? = null,
    onSharedHandled: () -> Unit = {},
) {
    val vm: AssistantViewModel = (LocalContext.current.applicationContext as ArchangelApp).assistant
    val market: MarketViewModel = viewModel()
    val context = LocalContext.current
    val app = context.applicationContext as ArchangelApp
    val settings by vm.settings.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(AppScreen.CORE) }

    // ── Разрешения
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startListening()
    }
    fun listen() {
        when {
            vm.core == CoreState.LISTENING -> vm.stopListening()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> vm.startListening()
            else -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.confirmPending()
    }

    // Уведомления нужны для напоминаний (Android 13+ спрашивает разрешение)
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(vm.needNotificationPermission) {
        if (vm.needNotificationPermission) {
            vm.needNotificationPermission = false
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Погода (местоположение) и планы (календарь)
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.permissionResult(it) }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.permissionResult(it) }
    LaunchedEffect(vm.needLocationPermission) {
        if (vm.needLocationPermission) {
            vm.needLocationPermission = false
            locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    LaunchedEffect(vm.needCalendarPermission) {
        if (vm.needCalendarPermission) {
            vm.needCalendarPermission = false
            calendarLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    // Жизненный цикл: режим ожидания слушает только когда приложение на экране
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> { vm.foreground = true; vm.refreshTasks() }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> { vm.foreground = false; vm.refreshStandby(0) }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Режим ожидания включили — нужен микрофон
    val standbyMicLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshStandby() }
    LaunchedEffect(settings.standby) {
        if (settings.standby && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            standbyMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Режим «умной колонки»: служба запускается, пока приложение открыто (так требует Android для микрофона)
    LaunchedEffect(settings.standby, settings.standbyBackground, settings.onboardingDone) {
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (settings.standby && settings.standbyBackground && settings.onboardingDone && mic) {
            com.archangel.ai.features.ListeningService.start(context)
            vm.needNotificationPermission = true
        } else com.archangel.ai.features.ListeningService.stop(context)
    }

    // «Поделиться → Архангел»
    LaunchedEffect(sharedText) {
        sharedText?.let { vm.checkShared(it); onSharedHandled() }
    }

    // Зрение: камера и галерея
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { vm.attachImage(it) }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.attachImage(it) }
    }

    // ── Команды от ассистента
    LaunchedEffect(vm.navigateTo) {
        vm.navigateTo?.let { screen = it; vm.navigateTo = null }
    }
    LaunchedEffect(vm.marketRequest) {
        vm.marketRequest?.let { market.select(it); vm.marketRequest = null }
    }
    LaunchedEffect(vm.lessonRequest) {
        vm.lessonRequest?.let { market.academy = it; vm.lessonRequest = null }
    }
    LaunchedEffect(launchAction) {
        when (launchAction) {
            LaunchAction.VOICE -> {
                screen = AppScreen.CORE
                if (vm.core != CoreState.LISTENING) listen()
            }
            LaunchAction.MARKET -> screen = AppScreen.MARKET
            LaunchAction.BRIEFING -> {
                screen = AppScreen.CORE
                vm.briefing()
            }
            null -> return@LaunchedEffect
        }
        onLaunchActionHandled()
    }
    BackHandler(enabled = screen != AppScreen.CORE) { screen = AppScreen.CORE }

    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // Приветствие голосом при запуске (после знакомства)
    LaunchedEffect(Unit) { vm.greetOnLaunch() }

    if (!settings.onboardingDone) {
        AmbientBackground {
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                OnboardingScreen(
                    vm = vm,
                    initial = settings,
                    onMic = { listen() },
                    onFinish = { done ->
                        app.settings.save(done)
                        screen = AppScreen.CORE
                    },
                )
            }
        }
        return
    }

    AmbientBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(180)) },
                label = "screen",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { target ->
                when (target) {
                    AppScreen.CORE -> CoreScreen(
                        state = vm.core,
                        level = vm.level,
                        partial = vm.partial,
                        hint = vm.hint,
                        settings = settings,
                        lastReply = lastAssistantReply(vm.messages),
                        onCoreTap = {
                            if (vm.core == CoreState.IDLE) listen() else vm.onCoreTap()
                        },
                        onQuickCommand = { vm.send(it) },
                    )
                    AppScreen.CHAT -> ChatScreen(
                        messages = vm.messages,
                        state = vm.core,
                        partial = vm.partial,
                        draft = vm.draft,
                        onDraftChange = { vm.draft = it },
                        onSend = { vm.send(vm.draft) },
                        onMic = { listen() },
                        pendingImage = vm.pendingImage,
                        onCamera = {
                            val u = com.archangel.ai.features.ImageUtil.cameraUri(context)
                            cameraUri = u
                            cameraLauncher.launch(u)
                        },
                        onGallery = {
                            galleryLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onClearImage = { vm.clearImage() },
                    )
                    AppScreen.TASKS -> TasksScreen(
                        notes = vm.notes,
                        reminders = vm.reminders,
                        onRefresh = { vm.refreshTasks() },
                        onAddNote = { vm.addNote(it) },
                        onDeleteNote = { vm.deleteNote(it) },
                        onDeleteReminder = { vm.deleteReminder(it) },
                        onSay = { vm.send(it) },
                    )
                    AppScreen.MARKET -> MarketScreen(
                        vm = market,
                        settings = settings,
                        onOrder = { vm.request(it) },
                        onAlertAdded = { vm.needNotificationPermission = true },
                        onSpeak = { vm.speakLine(it) },
                        onSignalsOn = { vm.needNotificationPermission = true },
                    )
                    AppScreen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        voices = vm.voiceOptions,
                        memory = vm.memoryFacts,
                        onForgetFact = { vm.forgetFact(it) },
                        onSave = { saved ->
                            app.settings.save(saved)
                            market.loadBalances()
                            if (saved.briefingEnabled) {
                                BriefingScheduler.schedule(context, saved.briefingHour, saved.briefingMinute)
                                vm.needNotificationPermission = true
                                if (!Weather.hasLocationPermission(context)) vm.needLocationPermission = true
                            } else BriefingScheduler.cancel(context)
                        },
                        onClearHistory = { vm.clearHistory() },
                        onPreviewVoice = { vm.previewVoice(it) },
                        onTestBrain = vm::testBrain,
                        onRestartOnboarding = { app.settings.update { it.copy(onboardingDone = false) } },
                    )
                }
            }
            if (!imeVisible) Dock(screen, onSelect = { screen = it }, listening = vm.core == CoreState.LISTENING)
        }
    }

    vm.pending?.let { p ->
        ConfirmDialog(
            p,
            onCancel = { vm.cancelPending() },
            onConfirm = {
                val needs = when (p.action) {
                    is PhoneAction.Call -> listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
                    is PhoneAction.Sms -> listOf(Manifest.permission.READ_CONTACTS)
                    else -> emptyList()
                }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                if (needs.isEmpty()) vm.confirmPending(onOrderDone = { market.loadBalances() })
                else phoneLauncher.launch(needs.toTypedArray())
            },
        )
    }
}

private val dockItems = listOf(
    AppScreen.CORE to "Ядро",
    AppScreen.CHAT to "Диалог",
    AppScreen.TASKS to "Дела",
    AppScreen.MARKET to "Рынок",
    AppScreen.SETTINGS to "Настройки",
)

/** Нижняя панель: моноширинные подписи и тлеющая точка над активным разделом. */
@Composable
private fun Dock(current: AppScreen, onSelect: (AppScreen) -> Unit, listening: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Hairline)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dockItems.forEach { (s, label) ->
                val active = s == current
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(s) }
                        .semantics {
                            role = Role.Tab
                            selected = active
                        }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val dotColor = when {
                        s == AppScreen.CORE && listening -> Ember
                        active -> Ember
                        else -> Color.Transparent
                    }
                    Box(
                        Modifier
                            .size(4.dp)
                            .background(dotColor, CircleShape)
                    )
                    Spacer(Modifier.height(6.dp))
                    MonoLabel(label, color = if (active) Bone else Smoke)
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(p: PendingAction, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Surface1, RoundedCornerShape(28.dp))
                .border(1.dp, if (p.danger) Ember else Hairline, RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(Ember, CircleShape)
                )
                Spacer(Modifier.size(8.dp))
                MonoLabel("Нужно ваше подтверждение", color = Ember)
            }
            Spacer(Modifier.height(14.dp))
            Text(p.title, style = TextStyles.Display.copy(fontSize = 28.sp))
            Spacer(Modifier.height(10.dp))
            Text(p.details, style = TextStyles.Body.copy(color = Ash))
            if (p.danger) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Это сделка на реальные деньги. Отменить её после исполнения нельзя.",
                    style = TextStyles.BodySmall.copy(color = Ember),
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Отмена", onClick = onCancel, modifier = Modifier.weight(1f))
                Pill(
                    if (p.danger) "Подтверждаю" else "Да",
                    onClick = onConfirm,
                    accent = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

