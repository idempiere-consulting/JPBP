package jpiere.base.plugin.org.adempiere.model;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;

import org.compiere.util.DB;

public class MCorporationGroup extends X_JP_CorporationGroup {

	public MCorporationGroup(Properties ctx, int JP_CorporationGroup_ID,
			String trxName) {
		super(ctx, JP_CorporationGroup_ID, trxName);
		// TODO 自動生成されたコンストラクター・スタブ
	}

	public MCorporationGroup(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
		// TODO 自動生成されたコンストラクター・スタブ
	}


	private MCorporation[] m_Corporations = null;

	public MCorporation[] getCorporations (boolean requery)
	{
		if(m_Corporations != null && !requery)
			return m_Corporations;

		ArrayList<MCorporation> list = new ArrayList<MCorporation>();
		final String sql = "SELECT JP_Corporation_ID FROM JP_GroupCorporations WHERE JP_CorporationGroup_ID=? AND IsActive='Y'";
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, get_TrxName());
			pstmt.setInt(1, get_ID());
			rs = pstmt.executeQuery();
			while (rs.next())
				list.add(new MCorporation (getCtx(), rs.getInt(1), get_TrxName()));
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, sql, e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null; pstmt = null;
		}

		m_Corporations = new MCorporation[list.size()];
		list.toArray(m_Corporations);
		return m_Corporations;
	}

	public MCorporation[] getCorporations()
	{
		return getCorporations (false);
	}


}