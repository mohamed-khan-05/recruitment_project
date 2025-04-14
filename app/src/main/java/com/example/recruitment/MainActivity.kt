package com.example.recruitment

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.recruitment.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } else {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            val navView: BottomNavigationView = binding.navView
            val navController = findNavController(R.id.nav_host_fragment_activity_main)
            val appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.navigation_home,
                    R.id.navigation_jobs,
                    R.id.navigation_notifications,
                    R.id.navigation_chat,
                    R.id.navigation_profile
                )
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            navView.setupWithNavController(navController)

            // Custom listener to navigate to the correct fragment when "Jobs" is clicked
            navView.setOnNavigationItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navigation_jobs -> {
                        // Reset the navigation stack and navigate to JobsFragment
                        navController.popBackStack(R.id.navigation_jobs, false)
                        navController.navigate(R.id.navigation_jobs)
                        true
                    }

                    else -> {
                        // Navigate to the selected fragment
                        navController.navigate(item.itemId)
                        true
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
