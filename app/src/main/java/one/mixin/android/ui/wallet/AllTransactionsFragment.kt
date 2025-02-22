package one.mixin.android.ui.wallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.paging.PagedList
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersDecoration
import kotlinx.android.synthetic.main.fragment_all_transactions.*
import kotlinx.android.synthetic.main.layout_empty_transaction.*
import kotlinx.android.synthetic.main.view_title.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import one.mixin.android.Constants
import one.mixin.android.R
import one.mixin.android.extension.navigate
import one.mixin.android.ui.common.UserBottomSheetDialogFragment
import one.mixin.android.ui.wallet.TransactionFragment.Companion.ARGS_SNAPSHOT
import one.mixin.android.ui.wallet.TransactionsFragment.Companion.ARGS_ASSET
import one.mixin.android.ui.wallet.adapter.OnSnapshotListener
import one.mixin.android.ui.wallet.adapter.SnapshotPagedAdapter
import one.mixin.android.vo.SnapshotItem
import one.mixin.android.vo.SnapshotType

class AllTransactionsFragment : BaseTransactionsFragment<PagedList<SnapshotItem>>(), OnSnapshotListener {

    companion object {
        const val TAG = "AllTransactionsFragment"
    }

    private val adapter = SnapshotPagedAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        layoutInflater.inflate(R.layout.fragment_all_transactions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title_view.left_ib.setOnClickListener { view.findNavController().navigateUp() }
        title_view.right_animator.setOnClickListener { showFiltersSheet() }
        adapter.listener = this
        transactions_rv.itemAnimator = null
        transactions_rv.adapter = adapter
        transactions_rv.addItemDecoration(StickyRecyclerHeadersDecoration(adapter))
        dataObserver = Observer { pagedList ->
            if (pagedList != null && pagedList.isNotEmpty()) {
                showEmpty(false)
                val opponentIds = pagedList.filter {
                    it?.opponentId != null
                }.map {
                    it.opponentId!!
                }
                walletViewModel.checkAndRefreshUsers(opponentIds)
            } else {
                showEmpty(true)
            }
            adapter.submitList(pagedList)

            if (!refreshedSnapshots) {
                walletViewModel.refreshSnapshots()
                refreshedSnapshots = true
            }
        }
        bindLiveData(walletViewModel.allSnapshots(initialLoadKey = initialLoadKey, orderByAmount = currentOrder == R.id.sort_amount))
    }

    override fun <T> onNormalItemClick(item: T) {
        lifecycleScope.launch(Dispatchers.IO) {
            val snapshot = item as SnapshotItem
            val a = walletViewModel.simpleAssetItem(snapshot.assetId)
            a?.let {
                if (!isAdded) return@launch

                view?.navigate(
                    R.id.action_all_transactions_fragment_to_transaction_fragment,
                    Bundle().apply {
                        putParcelable(ARGS_SNAPSHOT, snapshot)
                        putParcelable(ARGS_ASSET, it)
                    }
                )
            }
        }
    }

    override fun onUserClick(userId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            walletViewModel.getUser(userId)?.let {
                val f = UserBottomSheetDialogFragment.newInstance(it)
                f.showUserTransactionAction = {
                    view?.navigate(
                        R.id.action_all_transactions_to_user_transactions,
                        Bundle().apply { putString(Constants.ARGS_USER_ID, userId) }
                    )
                }
                f.show(parentFragmentManager, UserBottomSheetDialogFragment.TAG)
            }
        }
    }

    override fun refreshSnapshots() {
        walletViewModel.refreshSnapshots(offset = refreshOffset)
    }

    override fun onApplyClick() {
        val orderByAmount = currentOrder == R.id.sort_amount
        when (currentType) {
            R.id.filters_radio_all -> {
                bindLiveData(walletViewModel.allSnapshots(initialLoadKey = initialLoadKey, orderByAmount = orderByAmount))
            }
            R.id.filters_radio_transfer -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.transfer.name, SnapshotType.pending.name, initialLoadKey = initialLoadKey, orderByAmount = orderByAmount))
            }
            R.id.filters_radio_deposit -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.deposit.name, initialLoadKey = initialLoadKey, orderByAmount = orderByAmount))
            }
            R.id.filters_radio_withdrawal -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.withdrawal.name, initialLoadKey = initialLoadKey, orderByAmount = orderByAmount))
            }
            R.id.filters_radio_fee -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.fee.name, initialLoadKey = initialLoadKey, orderByAmount = orderByAmount))
            }
            R.id.filters_radio_rebate -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.rebate.name, initialLoadKey = initialLoadKey, orderByAmount = orderByAmount))
            }
            R.id.filters_radio_raw -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.raw.name, initialLoadKey = initialLoadKey, orderByAmount = orderByAmount))
            }
        }
        filtersSheet.dismiss()
    }

    private fun showEmpty(show: Boolean) {
        if (show) {
            if (empty_rl.visibility == GONE) {
                empty_rl.visibility = VISIBLE
            }
            if (transactions_rv.visibility == VISIBLE) {
                transactions_rv.visibility = GONE
            }
        } else {
            if (empty_rl.visibility == VISIBLE) {
                empty_rl.visibility = GONE
            }
            if (transactions_rv.visibility == GONE) {
                transactions_rv.visibility = VISIBLE
            }
        }
    }
}
