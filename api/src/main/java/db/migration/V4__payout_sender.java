package db.migration;

import org.flywaydb.core.api.migration.*;

public class V4__payout_sender extends BaseJavaMigration {
  public void migrate(Context context) throws Exception {
    String sender =
        System.getenv()
            .getOrDefault("SENDER_ADDRESS", "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266")
            .toLowerCase(java.util.Locale.ROOT);
    if (!sender.matches("0x[0-9a-f]{40}"))
      throw new IllegalArgumentException("Invalid legacy SENDER_ADDRESS");
    try (var statement = context.getConnection().createStatement()) {
      statement.execute("ALTER TABLE payouts ADD COLUMN sender_address text");
      statement.execute(
          "UPDATE payouts p SET sender_address=lower(ct.sender_address) FROM chain_transactions ct WHERE ct.payout_id=p.id");
    }
    try (var statement =
        context
            .getConnection()
            .prepareStatement("UPDATE payouts SET sender_address=? WHERE sender_address IS NULL")) {
      statement.setString(1, sender);
      statement.executeUpdate();
    }
    try (var statement = context.getConnection().createStatement()) {
      statement.execute("ALTER TABLE payouts ALTER COLUMN sender_address SET NOT NULL");
    }
  }
}
