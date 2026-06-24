package net.fabricmc.fabric.api.attachment.v1;

public final class AttachmentSyncPredicate {
	private static final AttachmentSyncPredicate TARGET_ONLY = new AttachmentSyncPredicate();

	private AttachmentSyncPredicate() {}

	public static AttachmentSyncPredicate targetOnly() {
		return TARGET_ONLY;
	}
}
