package com.bma.android.setup.fragments

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bma.android.setup.SetupActivity
import com.bma.android.setup.SetupViewModel

abstract class BaseSetupFragment : Fragment() {
    protected val setupActivity: SetupActivity
        get() = requireActivity() as SetupActivity

    protected val viewModel: SetupViewModel
        get() = setupActivity.run {
            ViewModelProvider(this)[SetupViewModel::class.java]
        }

    protected fun navigateToNext() {
        setupActivity.navigateToNext(this)
        viewModel.moveToNextState()
    }

    protected open fun showError(message: String) {
        viewModel.setError(message)
    }

    protected fun clearError() {
        viewModel.clearError()
    }
} 