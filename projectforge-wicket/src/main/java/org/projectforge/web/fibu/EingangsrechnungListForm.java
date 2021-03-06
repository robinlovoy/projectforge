/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2014 Kai Reinhard (k.reinhard@micromata.de)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.web.fibu;

import org.apache.log4j.Logger;
import org.projectforge.business.fibu.EingangsrechnungDao;
import org.projectforge.business.fibu.EingangsrechnungsStatistik;

public class EingangsrechnungListForm extends AbstractRechnungListForm<EingangsrechnungListFilter, EingangsrechnungListPage>
{
  private static final long serialVersionUID = 2678813484329104564L;

  private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(EingangsrechnungListForm.class);

  @Override
  protected void init()
  {
    final EingangsrechnungDao eingangsrechnungDao = getParentPage().getBaseDao();
    this.years = eingangsrechnungDao.getYears();
    super.init();
  }

  @Override
  protected EingangsrechnungsStatistik getStats()
  {
    return parentPage.getEingangsrechnungsStatistik();
  }

  public EingangsrechnungListForm(final EingangsrechnungListPage parentPage)
  {
    super(parentPage);
  }

  @Override
  protected EingangsrechnungListFilter newSearchFilterInstance()
  {
    return new EingangsrechnungListFilter();
  }

  @Override
  protected Logger getLogger()
  {
    return log;
  }
}
