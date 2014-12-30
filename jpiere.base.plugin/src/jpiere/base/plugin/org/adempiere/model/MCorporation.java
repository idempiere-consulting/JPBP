package jpiere.base.plugin.org.adempiere.model;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.compiere.model.I_C_BPartner;
import org.compiere.model.MBPartner;
import org.compiere.model.Query;
import org.compiere.util.Util;


public class MCorporation extends X_JP_Corporation {

	public MCorporation(Properties ctx, int JP_Corporation_ID, String trxName) {
		super(ctx, JP_Corporation_ID, trxName);
		// TODO 自動生成されたコンストラクター・スタブ
	}

	public MCorporation(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
		// TODO 自動生成されたコンストラクター・スタブ
	}

	private MBPartner[] m_BPartners = null;

	public MBPartner[] getBPartners (String whereClause, String orderClause)
	{
		StringBuilder whereClauseFinal = new StringBuilder(" JP_Corporation_ID=? ");
		if(!Util.isEmpty(whereClause, true))
			whereClauseFinal.append(whereClause);
		if(orderClause.length()==0)
			orderClause += "Value" ;

		List<MBPartner> list = new Query(getCtx(), I_C_BPartner.Table_Name, whereClauseFinal.toString(), get_TrxName())
										.setParameters(get_ID())
										.list();

		return list.toArray(new MBPartner[list.size()]);
	}

	public MBPartner[] getBPartners (boolean requery, String orderBy)
	{
		if(m_BPartners != null && !requery)
			return m_BPartners;

		String orderClause = "";
		if(orderBy != null && orderBy.length()>0)
			orderClause += orderBy;
		else
			orderClause += "Value" ;

		m_BPartners = getBPartners (null,orderClause);
		return m_BPartners;
	}

	public MBPartner[] getBPartners ()
	{
		return getBPartners (false, null);
	}



	private MCorporationGroup[] m_CorporationGroups = null;

	public MCorporationGroup[] getCorporationGroups (boolean requery)
	{

		if(m_CorporationGroups != null && !requery)
			return m_CorporationGroups;


		StringBuilder whereClause = new StringBuilder(" JP_Corporation_ID=? ");
		//
		List<MGroupCorporations> list = new Query(getCtx(), I_JP_GroupCorporations.Table_Name, whereClause.toString(), get_TrxName())
										.setParameters(get_ID())
										.list();

		ArrayList<MCorporationGroup> CorporationGroupList = new ArrayList<MCorporationGroup>();

		for(MGroupCorporations gc :list)
		{
			CorporationGroupList.add(gc.getParent());
		}

		m_CorporationGroups = CorporationGroupList.toArray(new MCorporationGroup[CorporationGroupList.size()]);

		return m_CorporationGroups ;
	}


	public MCorporationGroup[] getCorporationGroups ()
	{
		return 	getCorporationGroups (false);
	}

	@Override
	protected boolean beforeSave(boolean newRecord) {

		return super.beforeSave(newRecord);
	}




}