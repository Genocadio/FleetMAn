package com.gocavgo.fleetman.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gocavgo.fleetman.MainActivity
import com.gocavgo.fleetman.ui.theme.FleetManTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.or

// Data classes for requests and responses
data class CompanyUserRequestDto(
    val companyCode: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String?,
    val password: String,
    val dateOfBirth: String?
)

data class CompanyUserResponseDto(
    val id: Long,
    val companyId: Long,
    val companyName: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String?,
    val status: String,
    val dateOfBirth: String?,
    val address: String?,
    val role: String
)

data class LoginRequestDto(
    val emailOrPhone: String,
    val password: String
)

data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long,
    val username: String,
    val email: String,
    val phone: String?,
    val userType: String,
    val isCompanyUser: Boolean,
    val companyId: Long?,
    val companyName: String?,
    val companyUserRole: String?
)

class AuthActivity : ComponentActivity() {
    private lateinit var credentialsManager: CredentialsManager
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        credentialsManager = CredentialsManager(applicationContext)

        // If already logged in, navigate to MainActivity
        if (credentialsManager.getLoginStatus()) {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this)   {
            val intent = Intent(this@AuthActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        setContent {
            FleetManTheme {
                AuthScreen()
            }
        }
    }



    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AuthScreen() {
        val context = LocalContext.current
        var isLoggedIn by remember { mutableStateOf(credentialsManager.getLoginStatus()) }
        var showLogin by remember { mutableStateOf(true) }
        var isLoading by remember { mutableStateOf(false) }

        // Login form states
        var loginEmailOrPhone by remember { mutableStateOf("") }
        var loginPassword by remember { mutableStateOf("") }
        var loginPasswordVisible by remember { mutableStateOf(false) }

        // Register form states
        var regCompanyCode by remember { mutableStateOf("") }
        var regFirstName by remember { mutableStateOf("") }
        var regLastName by remember { mutableStateOf("") }
        var regEmail by remember { mutableStateOf("") }
        var regPhone by remember { mutableStateOf("") }
        var regPassword by remember { mutableStateOf("") }
        var regPasswordVisible by remember { mutableStateOf(false) }
        var regDateOfBirth by remember { mutableStateOf(TextFieldValue("")) }

        fun formatDateInputWithCursor(input: TextFieldValue): TextFieldValue {
            val digits = input.text.filter { it.isDigit() }
            val builder = StringBuilder()
            var dashCount = 0
            for (i in digits.indices) {
                builder.append(digits[i])
                if (i == 3 || i == 5) {
                    builder.append('-')
                    dashCount++
                }
                if (i >= 7) break
            }
            // Calculate new cursor position
            val newCursor = (input.selection.end + dashCount).coerceAtMost(builder.length)
            return TextFieldValue(builder.toString(), selection = TextRange(newCursor))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF6366F1),
                            Color(0xFF8B5CF6)
                        )
                    )
                )
        ) {
            if (isLoggedIn) {
                // Show logged in user info
                LoggedInUserScreen(
                    onLogout = {
                        credentialsManager.clearUserData()
                        isLoggedIn = false
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Title
                    Text(
                        text = "FleetMan",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = if (showLogin) "Sign in to your account" else "Create your account",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 32.dp)
                    )

                    // Auth Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp)
                        ) {
                            // Toggle Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TextButton(
                                    onClick = { showLogin = true },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (showLogin) Color(0xFF6366F1) else Color.Gray
                                    )
                                ) {
                                    Text(
                                        "Login",
                                        fontWeight = if (showLogin) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                TextButton(
                                    onClick = { showLogin = false },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (!showLogin) Color(0xFF6366F1) else Color.Gray
                                    )
                                ) {
                                    Text(
                                        "Register",
                                        fontWeight = if (!showLogin) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            if (showLogin) {
                                // Login Form
                                OutlinedTextField(
                                    value = loginEmailOrPhone,
                                    onValueChange = { loginEmailOrPhone = it },
                                    label = { Text("Email or Phone") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = loginPassword,
                                    onValueChange = { loginPassword = it },
                                    label = { Text("Password") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            loginPasswordVisible = !loginPasswordVisible
                                        }) {
                                            Icon(
                                                imageVector = if (loginPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                contentDescription = if (loginPasswordVisible) "Hide password" else "Show password"
                                            )
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        if (loginEmailOrPhone.isNotBlank() && loginPassword.isNotBlank()) {
                                            isLoading = true
                                            login(
                                                context = context,
                                                emailOrPhone = loginEmailOrPhone,
                                                password = loginPassword,
                                                onSuccess = { 
                                                    // Navigate to MainActivity after successful login
                                                    val intent = Intent(this@AuthActivity, MainActivity::class.java)
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    startActivity(intent)
                                                    finish()
                                                },
                                                onComplete = { isLoading = false }
                                            )
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Please fill all fields",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isLoading,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(
                                            0xFF6366F1
                                        )
                                    )
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Login", color = Color.White)
                                    }
                                }
                            } else {
                                // Register Form
                                OutlinedTextField(
                                    value = regCompanyCode,
                                    onValueChange = { regCompanyCode = it },
                                    label = { Text("Company Code") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row {
                                    OutlinedTextField(
                                        value = regFirstName,
                                        onValueChange = { regFirstName = it },
                                        label = { Text("First Name") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = regLastName,
                                        onValueChange = { regLastName = it },
                                        label = { Text("Last Name") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 8.dp),
                                        singleLine = true
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = regEmail,
                                    onValueChange = { regEmail = it },
                                    label = { Text("Email") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = regPhone,
                                    onValueChange = { regPhone = it },
                                    label = { Text("Phone (Optional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = regDateOfBirth,
                                    onValueChange = {
                                        regDateOfBirth = formatDateInputWithCursor(it)
                                    },
                                    label = { Text("Date of Birth (YYYY-MM-DD)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("2000-01-01") }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = regPassword,
                                    onValueChange = { regPassword = it },
                                    label = { Text("Password") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = if (regPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            regPasswordVisible = !regPasswordVisible
                                        }) {
                                            Icon(
                                                imageVector = if (regPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                contentDescription = if (regPasswordVisible) "Hide password" else "Show password"
                                            )
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        if (regCompanyCode.isNotBlank() && regFirstName.isNotBlank() &&
                                            regLastName.isNotBlank() && regEmail.isNotBlank() && regPassword.isNotBlank()
                                        ) {
                                            isLoading = true
                                            register(
                                                context = context,
                                                companyCode = regCompanyCode,
                                                firstName = regFirstName,
                                                lastName = regLastName,
                                                email = regEmail,
                                                phone = regPhone.takeIf { it.isNotBlank() },
                                                password = regPassword,
                                                dateOfBirth = regDateOfBirth.text.takeIf { regDateOfBirth.text.isNotBlank() },
                                                onSuccess = { showLogin = true },
                                                onComplete = { isLoading = false }
                                            )
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Please fill all required fields",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isLoading,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(
                                            0xFF10B981
                                        )
                                    )
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Register", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun LoggedInUserScreen(onLogout: () -> Unit) {
        val userName = credentialsManager.getUserName()
        val companyName = credentialsManager.getCompanyName()
        val userId = credentialsManager.getUserId()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Welcome!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6366F1),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "User: $userName",
                        fontSize = 16.sp,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Company: $companyName",
                        fontSize = 16.sp,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "User ID: $userId",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Logout", color = Color.White)
                    }
                }
            }
        }
    }

    private fun register(
        context: Context,
        companyCode: String,
        firstName: String,
        lastName: String,
        email: String,
        phone: String?,
        password: String,
        dateOfBirth: String?,
        onSuccess: () -> Unit,
        onComplete: () -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requestDto = CompanyUserRequestDto(
                    companyCode = companyCode.uppercase(),
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    phone = phone,
                    password = password,
                    dateOfBirth = dateOfBirth
                )

                val json = JSONObject().apply {
                    put("companyCode", requestDto.companyCode.toUpperCase())
                    put("firstName", requestDto.firstName)
                    put("lastName", requestDto.lastName)
                    put("email", requestDto.email)
                    requestDto.phone?.let { put("phone", it) }
                    put("password", requestDto.password)
                    requestDto.dateOfBirth?.let { put("dateOfBirth", it) }
                }

                val requestBody =
                    json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("https://api.gocavgo.com/api/main/staff")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(
                            context,
                            "Registration successful! Please login.",
                            Toast.LENGTH_LONG
                        ).show()
                        onSuccess()
                    } else {
                        val errorBody = response.body?.string()
                        Log.e("AuthActivity", "Registration error: $errorBody")
                        Toast.makeText(
                            context,

                            "Registration failed: ${response.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    onComplete()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                    onComplete()
                }
            }
        }
    }



    private fun login(
        context: Context,
        emailOrPhone: String,
        password: String,
        onSuccess: () -> Unit,
        onComplete: () -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requestDto = LoginRequestDto(
                    emailOrPhone = emailOrPhone,
                    password = password
                )

                val json = JSONObject().apply {
                    put("emailOrPhone", requestDto.emailOrPhone)
                    put("password", requestDto.password)
                }

                val requestBody =
                    json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("https://api.gocavgo.com/api/main/auth/login")
                    .post(requestBody)
                    .build()

                Log.d("AuthActivity", "Request URL: ${request.url}")
                Log.d("AuthActivity", "Request Body: ${json.toString()}")

                val response = client.newCall(request).execute()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        responseBody?.let { body ->
                            val jsonResponse = JSONObject(body)

                            // Use CredentialsManager to save user data
                            credentialsManager.saveUserData(
                                accessToken = jsonResponse.getString("accessToken"),
                                refreshToken = jsonResponse.getString("refreshToken"),
                                userId = jsonResponse.getLong("userId"),
                                username = jsonResponse.getString("username"),
                                companyName = jsonResponse.optString("companyName", ""),
                                companyId = jsonResponse.optLong("companyId", 0L)
                            )

                            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                            onSuccess()
                        }
                    } else {
                        Log.e("AuthActivity", "Login error: ${response.message}")
                        Toast.makeText(
                            context,
                            "Login failed: ${response.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    onComplete()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("AuthActivity", "Network error: ${e.message}", e)
                    Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                    onComplete()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AuthScreenPreview() {
    FleetManTheme {
        // Preview would need to be implemented separately due to activity dependencies
    }
}