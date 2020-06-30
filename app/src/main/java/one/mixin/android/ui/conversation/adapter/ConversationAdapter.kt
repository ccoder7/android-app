package one.mixin.android.ui.conversation.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.collection.ArraySet
import androidx.paging.PagedList
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.uber.autodispose.ScopeProvider
import com.uber.autodispose.autoDispose
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.item_chat_unread.view.*
import one.mixin.android.Constants.PAGE_SIZE
import one.mixin.android.R
import one.mixin.android.RxBus
import one.mixin.android.event.BlinkEvent
import one.mixin.android.extension.hashForDate
import one.mixin.android.extension.isSameDay
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.ui.common.recyclerview.SafePagedListAdapter
import one.mixin.android.ui.conversation.holder.ActionCardHolder
import one.mixin.android.ui.conversation.holder.ActionHolder
import one.mixin.android.ui.conversation.holder.AudioHolder
import one.mixin.android.ui.conversation.holder.AudioQuoteHolder
import one.mixin.android.ui.conversation.holder.BaseMentionHolder
import one.mixin.android.ui.conversation.holder.BaseViewHolder
import one.mixin.android.ui.conversation.holder.BillHolder
import one.mixin.android.ui.conversation.holder.CallHolder
import one.mixin.android.ui.conversation.holder.CardHolder
import one.mixin.android.ui.conversation.holder.ContactCardHolder
import one.mixin.android.ui.conversation.holder.ContactCardQuoteHolder
import one.mixin.android.ui.conversation.holder.FileHolder
import one.mixin.android.ui.conversation.holder.FileQuoteHolder
import one.mixin.android.ui.conversation.holder.HyperlinkHolder
import one.mixin.android.ui.conversation.holder.ImageHolder
import one.mixin.android.ui.conversation.holder.ImageQuoteHolder
import one.mixin.android.ui.conversation.holder.LocationHolder
import one.mixin.android.ui.conversation.holder.PostHolder
import one.mixin.android.ui.conversation.holder.RecallHolder
import one.mixin.android.ui.conversation.holder.SecretHolder
import one.mixin.android.ui.conversation.holder.StickerHolder
import one.mixin.android.ui.conversation.holder.StrangerHolder
import one.mixin.android.ui.conversation.holder.SystemHolder
import one.mixin.android.ui.conversation.holder.TextHolder
import one.mixin.android.ui.conversation.holder.TextQuoteHolder
import one.mixin.android.ui.conversation.holder.TimeHolder
import one.mixin.android.ui.conversation.holder.TransparentHolder
import one.mixin.android.ui.conversation.holder.UnknownHolder
import one.mixin.android.ui.conversation.holder.VideoHolder
import one.mixin.android.ui.conversation.holder.VideoQuoteHolder
import one.mixin.android.ui.conversation.holder.WaitingHolder
import one.mixin.android.util.markdown.MarkwonUtil
import one.mixin.android.vo.AppCardData
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.MessageItem
import one.mixin.android.vo.MessageStatus
import one.mixin.android.vo.User
import one.mixin.android.vo.create
import one.mixin.android.vo.isCallMessage
import one.mixin.android.vo.isRecall
import one.mixin.android.widget.MixinStickyRecyclerHeadersAdapter
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class ConversationAdapter(
    private val context: Context,
    private val keyword: String?,
    private val onItemListener: OnItemListener,
    private val isGroup: Boolean,
    private val isSecret: Boolean = true,
    private val isBot: Boolean = false
) : SafePagedListAdapter<MessageItem, RecyclerView.ViewHolder>(diffCallback),
    MixinStickyRecyclerHeadersAdapter<TimeHolder> {
    var selectSet: ArraySet<MessageItem> = ArraySet()
    var unreadMsgId: String? = null
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }
    var recipient: User? = null
    val miniMarkwon by lazy {
        MarkwonUtil.getMiniMarkwon(context)
    }
    var hasBottomView = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun hasAttachView(position: Int): Boolean = if (unreadMsgId != null) {
        getItem(position)?.messageId == unreadMsgId
    } else {
        false
    }

    private val publisher = PublishSubject.create<PagedList<MessageItem>>()
    private var pagingList: PagedList<MessageItem>? = null

    fun loadAround(index: Int) {
        pagingList?.loadAround(index)
    }

    override fun submitList(pagedList: PagedList<MessageItem>?) {
        if (pagedList == null) {
            super.submitList(null)
        } else {
            publisher.onNext(pagedList)
        }
        this.pagingList = pagedList
    }

    fun listen(scopeProvider: ScopeProvider) {
        publisher.throttleLast(120, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(AndroidSchedulers.mainThread())
            .autoDispose(scopeProvider)
            .subscribe(
                {
                    super.submitList(it)
                },
                {
                    Timber.e(it)
                }
            )
    }

    override fun onCreateAttach(parent: ViewGroup): View =
        LayoutInflater.from(parent.context).inflate(R.layout.item_chat_unread, parent, false)

    override fun onBindAttachView(view: View) {
        unreadMsgId?.let {
            view.unread_tv.text = view.context.getString(R.string.unread)
        }
    }

    fun markRead() {
        unreadMsgId?.let {
            unreadMsgId = null
        }
    }

    override fun getHeaderId(position: Int): Long = getItem(position).notNullWithElse(
        {
            abs(it.createdAt.hashForDate())
        },
        0
    )

    override fun onCreateHeaderViewHolder(parent: ViewGroup): TimeHolder =
        TimeHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_time, parent, false)
        )

    override fun onBindHeaderViewHolder(holder: TimeHolder, position: Int) {
        getItem(position)?.let {
            holder.bind(it.createdAt)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        getItem(position)?.let {
            when (getItemViewType(position)) {
                TEXT_TYPE -> {
                    (holder as TextHolder).bind(
                        it,
                        keyword,
                        isLast(position),
                        isFirst(position),
                        selectSet.size > 0,
                        isSelect(position),
                        onItemListener
                    )
                }
                TEXT_QUOTE_TYPE -> {
                    (holder as TextQuoteHolder).bind(
                        it, keyword, isLast(position),
                        isFirst(position), selectSet.size > 0, isSelect(position), onItemListener
                    )
                }
                POST_TYPE -> {
                    (holder as PostHolder).bind(
                        it, isLast(position),
                        isFirst(position), selectSet.size > 0, isSelect(position), onItemListener,
                        miniMarkwon
                    )
                }
                IMAGE_TYPE -> {
                    (holder as ImageHolder).bind(
                        it,
                        isLast(position),
                        isFirst(position),
                        selectSet.size > 0,
                        isSelect(position),
                        onItemListener
                    )
                }
                IMAGE_QUOTE_TYPE -> {
                    (holder as ImageQuoteHolder).bind(
                        it,
                        isLast(position),
                        isFirst(position),
                        selectSet.size > 0,
                        isSelect(position),
                        onItemListener
                    )
                }
                VIDEO_TYPE -> {
                    (holder as VideoHolder).bind(
                        it,
                        isLast(position),
                        isFirst(position),
                        selectSet.size > 0,
                        isSelect(position),
                        onItemListener
                    )
                }
                VIDEO_QUOTE_TYPE -> {
                    (holder as VideoQuoteHolder).bind(
                        it,
                        isLast(position),
                        isFirst(position),
                        selectSet.size > 0,
                        isSelect(position),
                        onItemListener
                    )
                }
                AUDIO_TYPE -> {
                    (holder as AudioHolder).bind(
                        it, isFirst(position),
                        isLast(position), selectSet.size > 0, isSelect(position), onItemListener
                    )
                }
                AUDIO_QUOTE_TYPE -> {
                    (holder as AudioQuoteHolder).bind(
                        it, isFirst(position), isLast(position), selectSet.size > 0, isSelect(position), onItemListener
                    )
                }
                SYSTEM_TYPE -> {
                    (holder as SystemHolder).bind(
                        it,
                        selectSet.size > 0,
                        isSelect(position),
                        onItemListener
                    )
                }
                CARD_TYPE -> {
                    (holder as CardHolder).bind(it)
                }
                BILL_TYPE -> {
                    (holder as BillHolder).bind(
                        it, isLast(position),
                        selectSet.size > 0, isSelect(position), onItemListener
                    )
                }
                FILE_TYPE -> {
                    (holder as FileHolder).bind(
                        it, keyword, isFirst(position),
                        isLast(position), selectSet.size > 0, isSelect(position), onItemListener
                    )
                }
                FILE_QUOTE_TYPE -> {
                    (holder as FileQuoteHolder).bind(
                        it, keyword, isFirst(position),
                        isLast(position), selectSet.size > 0, isSelect(position), onItemListener
                    )
                }
                WAITING_TYPE -> {
                    (holder as WaitingHolder).bind(
                        it,
                        isLast(position),
                        isFirst(position),
                        onItemListener
                    )
                }
                STRANGER_TYPE -> {
                    (holder as StrangerHolder).bind(onItemListener, isBot)
                }
                UNKNOWN_TYPE -> {
                    (holder as UnknownHolder).bind(
                        it,
                        isLast(position),
                        isFirst(position),
                        selectSet.size > 0,
                        isSelect(position),
                        onItemListener
                    )
                }
                STICKER_TYPE -> {
                    (holder as StickerHolder).bind(
                        it,
                        isFirst(position),
                        selectSet.size > 0,
                        isSelect(position),
                        onItemListener
                    )
                }
                LINK_TYPE -> {
                    (holder as HyperlinkHolder).bind(
                        it,
                        keyword,
                        isLast(position),
                        isFirst(position),
                        selectSet.size > 0,
                        isSelect(position),
                        onItemListener
                    )
                }
                ACTION_TYPE -> {
                    (holder as ActionHolder).bind(
                        it,
                        isFirst(position),
                        selectSet.size > 0,
                        isSelect(position),
                        onItemListener
                    )
                }
                ACTION_CARD_TYPE -> {
                    (holder as ActionCardHolder).bind(
                        it,
                        isFirst(position),
                        isLast(position),
                        selectSet.size > 0,
                        isSelect(position),
                        onItemListener
                    )
                }
                CONTACT_CARD_TYPE -> {
                    (holder as ContactCardHolder).bind(
                        it, isFirst(position), isLast(position),
                        selectSet.size > 0, isSelect(position), onItemListener
                    )
                }
                CONTACT_CARD_QUOTE_TYPE -> {
                    (holder as ContactCardQuoteHolder).bind(
                        it, isFirst(position), isLast(position), selectSet.size > 0, isSelect(position), onItemListener
                    )
                }
                SECRET_TYPE -> {
                    (holder as SecretHolder).bind(onItemListener)
                }
                CALL_TYPE -> {
                    (holder as CallHolder).bind(
                        it,
                        isFirst(position),
                        selectSet.size > 0,
                        isSelect(position),
                        onItemListener
                    )
                }
                RECALL_TYPE -> {
                    (holder as RecallHolder).bind(
                        it,
                        isFirst(position),
                        isLast(position),
                        selectSet.size > 0,
                        isSelect(position),
                        onItemListener
                    )
                }
                LOCATION_TYPE -> {
                    (holder as LocationHolder).bind(
                        it, isLast(position),
                        isFirst(position), selectSet.size > 0, isSelect(position), onItemListener
                    )
                }
                else -> {
                }
            }
        }
    }

    private fun isSelect(position: Int): Boolean {
        return if (selectSet.isEmpty()) {
            false
        } else {
            selectSet.find { it.messageId == getItem(position)?.messageId } != null
        }
    }

    override fun isListLast(position: Int): Boolean {
        return position == 0
    }

    override fun isLast(position: Int): Boolean {
        val currentItem = getItem(position)
        val previousItem = previous(position)
        return when {
            currentItem == null ->
                false
            previousItem == null ->
                true
            currentItem.type == MessageCategory.SYSTEM_CONVERSATION.name ->
                true
            previousItem.type == MessageCategory.SYSTEM_CONVERSATION.name ->
                true
            previousItem.userId != currentItem.userId ->
                true
            !isSameDay(previousItem.createdAt, currentItem.createdAt) ->
                true
            else -> false
        }
    }

    private fun isFirst(position: Int): Boolean {
        return if (isGroup || recipient != null) {
            val currentItem = getItem(position)
            if (!isGroup && (recipient?.isBot() == false || recipient?.userId == currentItem?.userId)) {
                return false
            }
            val nextItem = next(position)
            when {
                currentItem == null ->
                    false
                nextItem == null ->
                    true
                nextItem.type == MessageCategory.SYSTEM_CONVERSATION.name ->
                    true
                nextItem.userId != currentItem.userId ->
                    true
                !isSameDay(nextItem.createdAt, currentItem.createdAt) ->
                    true
                else -> false
            }
        } else {
            false
        }
    }

    private fun previous(position: Int): MessageItem? {
        return if (position > 0) {
            getItem(position - 1)
        } else {
            null
        }
    }

    private fun next(position: Int): MessageItem? {
        return if (position < itemCount - 1) {
            getItem(position + 1)
        } else {
            null
        }
    }

    override fun onCurrentListChanged(
        previousList: PagedList<MessageItem>?,
        currentList: PagedList<MessageItem>?
    ) {
        super.onCurrentListChanged(previousList, currentList)
        if (currentList != null && previousList != null && previousList.size != 0) {
            val changeCount = currentList.size - previousList.size
            when {
                abs(changeCount) >= PAGE_SIZE -> notifyDataSetChanged()
                changeCount > 0 ->
                    for (i in 1 until changeCount + 1)
                        getItem(i)?.let {
                            RxBus.publish(BlinkEvent(it.messageId, isLast(i)))
                        }
                changeCount < 0 -> notifyDataSetChanged()
            }
        }
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + if (hasBottomView && isSecret) {
            2
        } else if (hasBottomView || isSecret) {
            1
        } else {
            0
        }
    }

    fun getRealItemCount(): Int {
        return super.getItemCount()
    }

    public override fun getItem(position: Int): MessageItem? {
        return if (position > itemCount - 1) {
            null
        } else if (isSecret && hasBottomView) {
            when (position) {
                0 -> create(
                    MessageCategory.STRANGER.name,
                    if (super.getItemCount() > 0) {
                        super.getItem(0)?.createdAt
                    } else {
                        null
                    }
                )
                itemCount - 1 -> create(
                    MessageCategory.SECRET.name,
                    if (super.getItemCount() > 0) {
                        super.getItem(super.getItemCount() - 1)?.createdAt
                    } else {
                        null
                    }
                )
                else -> super.getItem(position - 1)
            }
        } else if (isSecret) {
            if (position == itemCount - 1) {
                create(
                    MessageCategory.SECRET.name,
                    if (super.getItemCount() > 0) {
                        super.getItem(super.getItemCount() - 1)?.createdAt
                    } else {
                        null
                    }
                )
            } else {
                super.getItem(position)
            }
        } else if (hasBottomView) {
            if (position == 0) {
                create(
                    MessageCategory.STRANGER.name,
                    if (super.getItemCount() > 0) {
                        super.getItem(0)?.createdAt
                    } else {
                        null
                    }
                )
            } else {
                super.getItem(position - 1)
            }
        } else {
            super.getItem(position)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder =
        when (viewType) {
            TEXT_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_text, parent, false)
                TextHolder(item)
            }
            TEXT_QUOTE_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_text_quote, parent, false)
                TextQuoteHolder(item)
            }
            POST_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_post, parent, false)
                PostHolder(item)
            }
            IMAGE_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_image, parent, false)
                ImageHolder(item)
            }
            IMAGE_QUOTE_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_image_quote, parent, false)
                ImageQuoteHolder(item)
            }
            SYSTEM_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_system, parent, false)
                SystemHolder(item)
            }
            CARD_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_card, parent, false)
                CardHolder(item)
            }
            BILL_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_bill, parent, false)
                BillHolder(item)
            }
            WAITING_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_waiting, parent, false)
                WaitingHolder(item, onItemListener)
            }
            STRANGER_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_stranger, parent, false)
                StrangerHolder(item)
            }
            UNKNOWN_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_unknown, parent, false)
                UnknownHolder(item)
            }
            FILE_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_file, parent, false)
                FileHolder(item)
            }
            FILE_QUOTE_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_file_quote, parent, false)
                FileQuoteHolder(item)
            }
            AUDIO_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_audio, parent, false)
                AudioHolder(item)
            }
            AUDIO_QUOTE_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_audio_quote, parent, false)
                AudioQuoteHolder(item)
            }
            STICKER_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_sticker, parent, false)
                StickerHolder(item)
            }
            ACTION_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_action, parent, false)
                ActionHolder(item)
            }
            ACTION_CARD_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_action_card, parent, false)
                ActionCardHolder(item)
            }
            LINK_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_hyperlink, parent, false)
                HyperlinkHolder(item)
            }
            CONTACT_CARD_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_contact_card, parent, false)
                ContactCardHolder(item)
            }
            CONTACT_CARD_QUOTE_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_contact_card_quote, parent, false)
                ContactCardQuoteHolder(item)
            }
            VIDEO_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_video, parent, false)
                VideoHolder(item)
            }
            VIDEO_QUOTE_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_video_quote, parent, false)
                VideoQuoteHolder(item)
            }
            SECRET_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_secret, parent, false)
                SecretHolder(item)
            }
            CALL_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_call, parent, false)
                CallHolder(item)
            }
            RECALL_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_recall, parent, false)
                RecallHolder(item)
            }
            LOCATION_TYPE -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_location, parent, false)
                LocationHolder(item)
            }
            else -> {
                val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_transparent, parent, false)
                TransparentHolder(item)
            }
        }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        getItem(holder.layoutPosition)?.let {
            (holder as BaseViewHolder).listen(it.messageId)
            if (holder is BaseMentionHolder) {
                holder.onViewAttachedToWindow()
            }
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        (holder as BaseViewHolder).stopListen()
    }

    private fun getItemType(messageItem: MessageItem?): Int =
        messageItem.notNullWithElse(
            { item ->
                when {
                    item.status == MessageStatus.UNKNOWN.name -> UNKNOWN_TYPE
                    item.type == MessageCategory.STRANGER.name -> STRANGER_TYPE
                    item.type == MessageCategory.SECRET.name -> SECRET_TYPE
                    item.status == MessageStatus.FAILED.name -> WAITING_TYPE
                    item.type == MessageCategory.SIGNAL_TEXT.name || item.type == MessageCategory.PLAIN_TEXT.name -> {
                        if (!item.quoteId.isNullOrEmpty() && !item.quoteContent.isNullOrEmpty()) {
                            TEXT_QUOTE_TYPE
                        } else if (!item.siteName.isNullOrBlank() || !item.siteDescription.isNullOrBlank()) {
                            LINK_TYPE
                        } else {
                            TEXT_TYPE
                        }
                    }
                    item.type == MessageCategory.SIGNAL_IMAGE.name ||
                        item.type == MessageCategory.PLAIN_IMAGE.name -> {
                        if (!item.quoteId.isNullOrEmpty() && !item.quoteContent.isNullOrEmpty()) {
                            IMAGE_QUOTE_TYPE
                        } else {
                            IMAGE_TYPE
                        }
                    }
                    item.type == MessageCategory.SYSTEM_CONVERSATION.name -> SYSTEM_TYPE
                    item.type == MessageCategory.SYSTEM_ACCOUNT_SNAPSHOT.name -> BILL_TYPE
                    item.type == MessageCategory.SIGNAL_DATA.name ||
                        item.type == MessageCategory.PLAIN_DATA.name -> {
                        if (!item.quoteId.isNullOrEmpty() && !item.quoteContent.isNullOrEmpty()) {
                            FILE_QUOTE_TYPE
                        } else {
                            FILE_TYPE
                        }
                    }
                    item.type == MessageCategory.SIGNAL_STICKER.name ||
                        item.type == MessageCategory.PLAIN_STICKER.name -> STICKER_TYPE
                    item.type == MessageCategory.APP_BUTTON_GROUP.name -> ACTION_TYPE
                    item.type == MessageCategory.APP_CARD.name -> ACTION_CARD_TYPE
                    item.type == MessageCategory.SIGNAL_CONTACT.name ||
                        item.type == MessageCategory.PLAIN_CONTACT.name -> {
                        if (!item.quoteId.isNullOrEmpty() && !item.quoteContent.isNullOrEmpty()) {
                            CONTACT_CARD_QUOTE_TYPE
                        } else {
                            CONTACT_CARD_TYPE
                        }
                    }
                    item.type == MessageCategory.SIGNAL_VIDEO.name ||
                        item.type == MessageCategory.PLAIN_VIDEO.name ||
                        item.type == MessageCategory.SIGNAL_LIVE.name ||
                        item.type == MessageCategory.PLAIN_LIVE.name -> {
                        if (!item.quoteId.isNullOrEmpty() && !item.quoteContent.isNullOrEmpty()) {
                            VIDEO_QUOTE_TYPE
                        } else {
                            VIDEO_TYPE
                        }
                    }
                    item.type == MessageCategory.SIGNAL_AUDIO.name ||
                        item.type == MessageCategory.PLAIN_AUDIO.name -> {
                        if (!item.quoteId.isNullOrEmpty() && !item.quoteContent.isNullOrEmpty()) {
                            AUDIO_QUOTE_TYPE
                        } else {
                            AUDIO_TYPE
                        }
                    }
                    item.type == MessageCategory.PLAIN_POST.name ||
                        item.type == MessageCategory.SIGNAL_POST.name -> POST_TYPE
                    item.isCallMessage() -> CALL_TYPE
                    item.isRecall() -> RECALL_TYPE
                    item.type == MessageCategory.PLAIN_LOCATION.name ||
                        item.type == MessageCategory.SIGNAL_LOCATION.name -> LOCATION_TYPE
                    else -> UNKNOWN_TYPE
                }
            },
            NULL_TYPE
        )

    override fun getItemViewType(position: Int): Int = getItemType(getItem(position))

    companion object {
        const val NULL_TYPE = 99
        const val UNKNOWN_TYPE = 0
        const val TEXT_TYPE = 1
        const val TEXT_QUOTE_TYPE = -1
        const val IMAGE_TYPE = 2
        const val IMAGE_QUOTE_TYPE = -2
        const val LINK_TYPE = 3
        const val VIDEO_TYPE = 4
        const val VIDEO_QUOTE_TYPE = -4
        const val AUDIO_TYPE = 5
        const val AUDIO_QUOTE_TYPE = -5
        const val FILE_TYPE = 6
        const val FILE_QUOTE_TYPE = -6
        const val STICKER_TYPE = 7
        const val CONTACT_CARD_TYPE = 8
        const val CONTACT_CARD_QUOTE_TYPE = -8
        const val CARD_TYPE = 9
        const val BILL_TYPE = 10
        const val POST_TYPE = 11
        const val ACTION_TYPE = 12
        const val ACTION_CARD_TYPE = 13
        const val SYSTEM_TYPE = 14
        const val WAITING_TYPE = 15
        const val STRANGER_TYPE = 16
        const val SECRET_TYPE = 17
        const val CALL_TYPE = 18
        const val RECALL_TYPE = 19
        const val LOCATION_TYPE = 20

        private val diffCallback = object : DiffUtil.ItemCallback<MessageItem>() {
            override fun areItemsTheSame(oldItem: MessageItem, newItem: MessageItem): Boolean {
                return oldItem.messageId == newItem.messageId
            }

            override fun areContentsTheSame(
                oldItem: MessageItem,
                newItem: MessageItem
            ): Boolean {
                return oldItem.mediaStatus == newItem.mediaStatus &&
                    oldItem.type == newItem.type &&
                    oldItem.status == newItem.status &&
                    oldItem.userFullName == newItem.userFullName &&
                    oldItem.participantFullName == newItem.participantFullName &&
                    oldItem.sharedUserFullName == newItem.sharedUserFullName &&
                    oldItem.mediaSize == newItem.mediaSize &&
                    oldItem.quoteContent == newItem.quoteContent &&
                    oldItem.assetSymbol == newItem.assetSymbol &&
                    oldItem.assetUrl == newItem.assetUrl &&
                    oldItem.assetIcon == newItem.assetIcon &&
                    oldItem.mentionRead == newItem.mentionRead
            }
        }
    }

    open class OnItemListener {

        open fun onSelect(isSelect: Boolean, messageItem: MessageItem, position: Int) {}

        open fun onLongClick(messageItem: MessageItem, position: Int): Boolean = true

        open fun onImageClick(messageItem: MessageItem, view: View) {}

        open fun onFileClick(messageItem: MessageItem) {}

        open fun onAudioFileClick(messageItem: MessageItem) {}

        open fun onCancel(id: String) {}

        open fun onRetryUpload(messageId: String) {}

        open fun onRetryDownload(messageId: String) {}

        open fun onUserClick(userId: String) {}

        open fun onMentionClick(identityNumber: String) {}

        open fun onUrlClick(url: String) {}

        open fun onAddClick() {}

        open fun onBlockClick() {}

        open fun onActionClick(action: String, userId: String) {}

        open fun onAppCardClick(appCard: AppCardData, userId: String) {}

        open fun onAudioClick(messageItem: MessageItem) {}

        open fun onBillClick(messageItem: MessageItem) {}

        open fun onContactCardClick(userId: String) {}

        open fun onTransferClick(userId: String) {}

        open fun onQuoteMessageClick(messageId: String, quoteMessageId: String?) {}

        open fun onCallClick(messageItem: MessageItem) {}

        open fun onPostClick(view: View, messageItem: MessageItem) {}

        open fun onOpenHomePage() {}

        open fun onSayHi() {}

        open fun onLocationClick(messageItem: MessageItem) {}
    }

    fun addSelect(messageItem: MessageItem): Boolean {
        return selectSet.add(messageItem)
    }

    fun removeSelect(messageItem: MessageItem): Boolean {
        return selectSet.remove(selectSet.find { it.messageId == messageItem.messageId })
    }
}
