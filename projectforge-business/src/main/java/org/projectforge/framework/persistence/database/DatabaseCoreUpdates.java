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

package org.projectforge.framework.persistence.database;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Predicate;

import org.projectforge.business.address.AddressDO;
import org.projectforge.business.address.AddressDao;
import org.projectforge.business.fibu.AuftragDO;
import org.projectforge.business.fibu.AuftragsPositionDO;
import org.projectforge.business.fibu.AuftragsPositionsStatus;
import org.projectforge.business.fibu.AuftragsStatus;
import org.projectforge.business.fibu.EingangsrechnungDO;
import org.projectforge.business.fibu.EmployeeDO;
import org.projectforge.business.fibu.EmployeeDao;
import org.projectforge.business.fibu.EmployeeStatus;
import org.projectforge.business.fibu.EmployeeTimedDO;
import org.projectforge.business.fibu.KontoDO;
import org.projectforge.business.fibu.KundeDO;
import org.projectforge.business.fibu.PaymentScheduleDO;
import org.projectforge.business.fibu.ProjektDO;
import org.projectforge.business.fibu.RechnungDO;
import org.projectforge.business.fibu.RechnungsPositionDO;
import org.projectforge.business.fibu.api.EmployeeService;
import org.projectforge.business.multitenancy.TenantDao;
import org.projectforge.business.multitenancy.TenantRegistryMap;
import org.projectforge.business.multitenancy.TenantService;
import org.projectforge.business.orga.VisitorbookDO;
import org.projectforge.business.orga.VisitorbookTimedAttrDO;
import org.projectforge.business.orga.VisitorbookTimedAttrDataDO;
import org.projectforge.business.orga.VisitorbookTimedAttrWithDataDO;
import org.projectforge.business.orga.VisitorbookTimedDO;
import org.projectforge.business.scripting.ScriptDO;
import org.projectforge.business.task.TaskDO;
import org.projectforge.business.teamcal.TeamCalConfig;
import org.projectforge.business.teamcal.event.model.TeamEventDO;
import org.projectforge.business.user.GroupDao;
import org.projectforge.business.user.ProjectForgeGroup;
import org.projectforge.business.user.UserXmlPreferencesDO;
import org.projectforge.business.vacation.model.VacationDO;
import org.projectforge.business.vacation.repository.VacationDao;
import org.projectforge.continuousdb.DatabaseResultRow;
import org.projectforge.continuousdb.SchemaGenerator;
import org.projectforge.continuousdb.Table;
import org.projectforge.continuousdb.TableAttribute;
import org.projectforge.continuousdb.UpdateEntry;
import org.projectforge.continuousdb.UpdateEntryImpl;
import org.projectforge.continuousdb.UpdatePreCheckStatus;
import org.projectforge.continuousdb.UpdateRunningStatus;
import org.projectforge.framework.configuration.Configuration;
import org.projectforge.framework.configuration.ConfigurationType;
import org.projectforge.framework.configuration.entities.ConfigurationDO;
import org.projectforge.framework.persistence.attr.impl.InternalAttrSchemaConstants;
import org.projectforge.framework.persistence.entities.AbstractBaseDO;
import org.projectforge.framework.persistence.history.HistoryBaseDaoAdapter;
import org.projectforge.framework.persistence.jpa.PfEmgrFactory;
import org.projectforge.framework.persistence.user.entities.GroupDO;
import org.projectforge.framework.persistence.user.entities.PFUserDO;
import org.projectforge.framework.persistence.user.entities.TenantDO;
import org.projectforge.framework.persistence.user.entities.UserRightDO;
import org.projectforge.framework.time.DateFormats;
import org.projectforge.framework.time.DateHelper;
import org.springframework.context.ApplicationContext;

import de.micromata.genome.db.jpa.history.api.HistoryEntry;
import de.micromata.genome.db.jpa.tabattr.api.TimeableRow;
import de.micromata.genome.jpa.CriteriaUpdate;
import de.micromata.genome.jpa.metainf.EntityMetadata;
import de.micromata.genome.jpa.metainf.JpaMetadataEntityNotFoundException;

/**
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
public class DatabaseCoreUpdates
{
  private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(DatabaseCoreUpdates.class);

  public static final String CORE_REGION_ID = DatabaseCoreInitial.CORE_REGION_ID;

  private static final String VERSION_5_0 = "5.0";

  private static ApplicationContext applicationContext;
  private static String RESTART_RQUIRED = "no";

  static void setApplicationContext(final ApplicationContext applicationContext)
  {
    DatabaseCoreUpdates.applicationContext = applicationContext;
  }

  @SuppressWarnings("serial")
  public static List<UpdateEntry> getUpdateEntries()
  {
    final DatabaseUpdateService databaseUpdateService = applicationContext.getBean(DatabaseUpdateService.class);
    final PfEmgrFactory emf = applicationContext.getBean(PfEmgrFactory.class);
    final InitDatabaseDao initDatabaseDao = applicationContext.getBean(InitDatabaseDao.class);
    final TenantDao tenantDao = applicationContext.getBean(TenantDao.class);

    final List<UpdateEntry> list = new ArrayList<>();

    ////////////////////////////////////////////////////////////////////
    // 6.12.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.12.0", "2017-05-22",
        "Correct calendar exdates. Change address image data to AddressDO.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.12.0");
        if (hasISODates() || databaseUpdateService.doesTableAttributeExist("T_ADDRESS", "imagedata") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doesTableAttributeExist("T_ADDRESS", "imagedata") == false) {
          initDatabaseDao.updateSchema();
          migrateImageData();
          deleteImageHistoryData();
          deleteImageAddressAttrData();
          log.info("Address image data migration DONE.");
        }

        if (hasISODates()) {
          SimpleDateFormat iCalFormatterWithTime = new SimpleDateFormat(DateFormats.ICAL_DATETIME_FORMAT);
          SimpleDateFormat iCalFormatterAllDay = new SimpleDateFormat(DateFormats.COMPACT_DATE);
          List<SimpleDateFormat> formatterPatterns = Arrays
              .asList(new SimpleDateFormat(DateFormats.ISO_TIMESTAMP_SECONDS), new SimpleDateFormat(DateFormats.ISO_TIMESTAMP_MINUTES),
                  new SimpleDateFormat(DateFormats.ISO_DATE), iCalFormatterWithTime, iCalFormatterAllDay);
          List<DatabaseResultRow> resultList = databaseUpdateService
              .query(
                  "SELECT pk, recurrence_ex_date, all_day FROM t_plugin_calendar_event te WHERE te.recurrence_ex_date IS NOT NULL AND te.recurrence_ex_date <> ''");
          log.info("Found: " + resultList.size() + " event entries to update.");
          for (DatabaseResultRow row : resultList) {
            Integer id = (Integer) row.getEntry(0).getValue();
            String exDateList = (String) row.getEntry(1).getValue();
            Boolean allDay = (Boolean) row.getEntry(2).getValue();
            log.debug("Event with id: " + id + " has exdate value: " + exDateList);
            String[] exDateArray = exDateList.split(",");
            List<String> finalExDates = new ArrayList<>();
            for (String exDateOld : exDateArray) {
              Date oldDate = null;
              for (SimpleDateFormat sdf : formatterPatterns) {
                try {
                  oldDate = sdf.parse(exDateOld);
                  break;
                } catch (ParseException e) {
                  if (log.isDebugEnabled()) {
                    log.debug("Date not parsable. Try another parser.");
                  }
                }
              }
              if (oldDate == null) {
                log.error("Date not parsable. Ignoring it: " + exDateOld);
                continue;
              }
              if (allDay != null && allDay) {
                finalExDates.add(iCalFormatterAllDay.format(oldDate));
              } else {
                finalExDates.add(iCalFormatterWithTime.format(oldDate));
              }
            }
            String newExDateValue = String.join(",", finalExDates);
            try {
              databaseUpdateService.execute("UPDATE t_plugin_calendar_event SET recurrence_ex_date = '" + newExDateValue + "' WHERE pk = " + id);
            } catch (Exception e) {
              log.error("Error while updating event with id: " + id + " and new exdatevalue: " + newExDateValue + " . Ignoring it.");
            }
          }
          log.info("Exdate migration DONE.");
        }
        return UpdateRunningStatus.DONE;
      }

      private boolean hasISODates()
      {
        List<DatabaseResultRow> result = databaseUpdateService.query("SELECT * FROM T_PLUGIN_CALENDAR_EVENT WHERE recurrence_ex_date LIKE '%-%' LIMIT 1");
        return result.size() > 0;
      }

      private void deleteImageAddressAttrData()
      {
        List<DatabaseResultRow> attrResultList = databaseUpdateService.query("SELECT pk FROM t_address_attr WHERE propertyname = 'profileImageData'");
        for (DatabaseResultRow attrRow : attrResultList) {
          Integer attrId = (Integer) attrRow.getEntry(0).getValue();
          databaseUpdateService.execute("DELETE FROM t_address_attrdata WHERE parent_id = " + attrId);
          databaseUpdateService.execute("DELETE FROM t_address_attr WHERE pk = " + attrId);
        }
      }

      private void deleteImageHistoryData()
      {
        List<DatabaseResultRow> histAttrResultList = databaseUpdateService
            .query("SELECT pk FROM t_pf_history_attr WHERE propertyname LIKE '%attrs.profileImageData%'");
        for (DatabaseResultRow histAttrRow : histAttrResultList) {
          Long histAttrId = (Long) histAttrRow.getEntry(0).getValue();
          databaseUpdateService.execute("DELETE FROM t_pf_history_attr_data WHERE parent_pk = " + histAttrId);
          databaseUpdateService.execute("DELETE FROM t_pf_history_attr WHERE pk = " + histAttrId);
        }
      }

      private void migrateImageData()
      {
        AddressDao addressDao = applicationContext.getBean(AddressDao.class);
        List<AddressDO> allAddresses = addressDao.internalLoadAll();
        for (AddressDO ad : allAddresses) {
          byte[] imageData = ad.getAttribute("profileImageData", byte[].class);
          if (imageData != null && imageData.length > 0) {
            final PfEmgrFactory emf = applicationContext.getBean(PfEmgrFactory.class);
            emf.runInTrans(emgr -> {
              AddressDO addressDO = emgr.selectByPkAttached(AddressDO.class, ad.getId());
              addressDO.setImageData(imageData);
              emgr.update(addressDO);
              return null;
            });
          }
        }
      }
    });

    ////////////////////////////////////////////////////////////////////
    // 6.11.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.11.0", "2017-05-03",
        "Add discounts and konto informations. Add period of performance to invoices.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.11.0");
        if (isSchemaUpdateNecessary()) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (isSchemaUpdateNecessary()) {
          initDatabaseDao.updateSchema();
        }
        return UpdateRunningStatus.DONE;
      }

      private boolean isSchemaUpdateNecessary()
      {
        return databaseUpdateService.doesTableAttributeExist("t_fibu_eingangsrechnung", "discountmaturity") == false
            || databaseUpdateService.doesTableAttributeExist("t_fibu_rechnung", "discountmaturity") == false
            || databaseUpdateService.doesTableAttributeExist("t_fibu_eingangsrechnung", "customernr") == false
            || databaseUpdateService.doTableAttributesExist(RechnungDO.class, "periodOfPerformanceBegin", "periodOfPerformanceEnd") == false
            || databaseUpdateService.doTableAttributesExist(RechnungsPositionDO.class, "periodOfPerformanceType", "periodOfPerformanceBegin",
            "periodOfPerformanceEnd") == false;
      }
    });

    ////////////////////////////////////////////////////////////////////
    // 6.10.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.10.0", "2017-04-11",
        "Add column position_number to table T_FIBU_PAYMENT_SCHEDULE.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.10.0");
        if (databaseUpdateService.doesTableAttributeExist("T_FIBU_PAYMENT_SCHEDULE", "position_number") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doesTableAttributeExist("T_FIBU_PAYMENT_SCHEDULE", "position_number") == false) {
          initDatabaseDao.updateSchema();
        }
        return UpdateRunningStatus.DONE;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.9.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.9.0", "2017-03-15",
        "Allow multiple substitutions on application for leave.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.9.0");
        if (databaseUpdateService.doesTableExist("t_employee_vacation_substitution") == false ||
            databaseUpdateService.doesTableAttributeExist("t_employee_vacation", "substitution_id") ||
            databaseUpdateService.doesUniqueConstraintExists("T_PLUGIN_CALENDAR_EVENT", "unique_t_plugin_calendar_event_uid") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        final Optional<Boolean> isColumnNullable = databaseUpdateService.isColumnNullable("T_PLUGIN_CALENDAR_EVENT", "UID");
        if (isColumnNullable.isPresent() == false || isColumnNullable.get()) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doesTableExist("t_employee_vacation_substitution") == false
            || databaseUpdateService.doesUniqueConstraintExists("T_PLUGIN_CALENDAR_EVENT", "unique_t_plugin_calendar_event_uid") == false) {
          if (doesDuplicateUidsExists()) {
            handleDuplicateUids();
          }
          // Updating the schema
          initDatabaseDao.updateSchema();
        }

        final Optional<Boolean> isColumnNullable = databaseUpdateService.isColumnNullable("T_PLUGIN_CALENDAR_EVENT", "UID");
        if (isColumnNullable.isPresent() == false || isColumnNullable.get()) {
          databaseUpdateService.execute("ALTER TABLE t_plugin_calendar_event ALTER COLUMN uid SET NOT NULL;");
        }

        if (databaseUpdateService.doesTableAttributeExist("t_employee_vacation", "substitution_id")) {
          migrateSubstitutions();
          // drop old substitution column
          databaseUpdateService.dropTableAttribute("t_employee_vacation", "substitution_id");
        }

        return UpdateRunningStatus.DONE;
      }

      private void handleDuplicateUids()
      {
        final PfEmgrFactory emf = applicationContext.getBean(PfEmgrFactory.class);
        emf.runInTrans(emgr -> {
          List<DatabaseResultRow> resultSet = databaseUpdateService
              .query("SELECT uid, COUNT(*) FROM t_plugin_calendar_event GROUP BY uid HAVING COUNT(*) > 1");
          for (DatabaseResultRow resultLine : resultSet) {
            List<TeamEventDO> teList = emgr
                .selectAttached(TeamEventDO.class, "SELECT t FROM TeamEventDO t WHERE t.uid = :uid", "uid", resultLine.getEntry(0).getValue());
            for (TeamEventDO te : teList) {
              te.setUid(TeamCalConfig.get().createEventUid());
              emgr.update(te);
            }
          }
          return null;
        });
      }

      private boolean doesDuplicateUidsExists()
      {
        List<DatabaseResultRow> resultSet = databaseUpdateService.query("SELECT uid, COUNT(*) FROM t_plugin_calendar_event GROUP BY uid HAVING COUNT(*) > 1");
        return resultSet != null && resultSet.size() > 0;
      }

      // migrate from old substitution column to new t_employee_vacation_substitution table
      private void migrateSubstitutions()
      {
        final VacationDao vacationDao = applicationContext.getBean(VacationDao.class);
        final EmployeeDao employeeDao = applicationContext.getBean(EmployeeDao.class);

        final List<DatabaseResultRow> resultRows = databaseUpdateService
            .query("SELECT pk, substitution_id FROM t_employee_vacation WHERE substitution_id IS NOT NULL;");

        for (final DatabaseResultRow row : resultRows) {
          final int vacationId = (int) row.getEntry("pk").getValue();
          final int substitutionId = (int) row.getEntry("substitution_id").getValue();
          final VacationDO vacation = vacationDao.internalGetById(vacationId);
          final EmployeeDO substitution = employeeDao.internalGetById(substitutionId);
          vacation.getSubstitutions().add(substitution);
          vacationDao.internalUpdate(vacation);
        }
      }
    });

    ////////////////////////////////////////////////////////////////////
    // 6.8.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.8.0", "2017-02-15",
        "Add calendar to vacation." + "Add possibility to create applications for leave of a half day.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.8.0");
        if (databaseUpdateService.doesTableExist("t_employee_vacation_calendar") == false
            || databaseUpdateService.doesTableAttributeExist("t_employee_vacation", "is_half_day") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doesTableExist("t_employee_vacation_calendar") == false
            || databaseUpdateService.doesTableAttributeExist("t_employee_vacation", "is_half_day") == false) {
          //Updating the schema
          initDatabaseDao.updateSchema();
        }
        return UpdateRunningStatus.DONE;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.7.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.7.0", "2017-01-11",
        "Add payment type for order book position. Add users to project and order. Extend order position status.")
    {
      private static final String AUFTRAG_TABLE_COL_NAME = "status";
      private static final String AUFTRAG_OLD_STATUS_POTENZIAL = "GROB_KALKULATION";
      private final String AUFTRAG_NEW_STATUS_POTENZIAL = AuftragsStatus.POTENZIAL.name();

      private static final String AUFTRAG_POS_TABLE_COL_NAME = "status";
      private static final String AUFTRAG_POS_OLD_STATUS_BEAUFTRAGT = "BEAUFTRAGTE_OPTION";
      private final String AUFTRAG_POS_NEW_STATUS_BEAUFTRAGT = AuftragsPositionsStatus.BEAUFTRAGT.name();
      private static final String AUFTRAG_POS_OLD_STATUS_ABGELEHNT = "NICHT_BEAUFTRAGT";
      private final String AUFTRAG_POS_NEW_STATUS_ABGELEHNT = AuftragsPositionsStatus.ABGELEHNT.name();

      private boolean doesAuftragPotenzialNeedsUpdate()
      {
        return databaseUpdateService.doesTableRowExists(AuftragDO.class, AUFTRAG_TABLE_COL_NAME, AUFTRAG_OLD_STATUS_POTENZIAL, true);
      }

      private boolean doesAuftragPosBeauftragtNeedsUpdate()
      {
        return databaseUpdateService.doesTableRowExists(AuftragsPositionDO.class, AUFTRAG_POS_TABLE_COL_NAME, AUFTRAG_POS_OLD_STATUS_BEAUFTRAGT, true);
      }

      private boolean doesAuftragPosAbgelehntNeedsUpdate()
      {
        return databaseUpdateService.doesTableRowExists(AuftragsPositionDO.class, AUFTRAG_POS_TABLE_COL_NAME, AUFTRAG_POS_OLD_STATUS_ABGELEHNT, true);
      }

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.7.0");
        if (this.isUpdateFibuAuftragPositionRequired()
            || databaseUpdateService.doesTableAttributeExist("T_FIBU_PROJEKT", "projectmanager_fk") == false
            || databaseUpdateService.doesTableAttributeExist("T_FIBU_PROJEKT", "headofbusinessmanager_fk") == false
            || databaseUpdateService.doesTableAttributeExist("T_FIBU_PROJEKT", "salesmanager_fk") == false
            || databaseUpdateService.doesTableAttributeExist("t_fibu_auftrag", "projectmanager_fk") == false
            || databaseUpdateService.doesTableAttributeExist("t_fibu_auftrag", "headofbusinessmanager_fk") == false
            || databaseUpdateService.doesTableAttributeExist("t_fibu_auftrag", "salesmanager_fk") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        if (doesAuftragPotenzialNeedsUpdate() || doesAuftragPosBeauftragtNeedsUpdate() || doesAuftragPosAbgelehntNeedsUpdate()) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (this.isUpdateFibuAuftragPositionRequired()) {
          //Updating the schema
          initDatabaseDao.updateSchema();
          databaseUpdateService.execute("UPDATE t_fibu_auftrag_position SET paymentType = 'FESTPREISPAKET', art = NULL WHERE art = 'FESTPREISPAKET'");
          databaseUpdateService.execute("UPDATE t_fibu_auftrag_position SET paymentType = 'TIME_AND_MATERIALS', art = NULL WHERE art = 'TIME_AND_MATERIALS'");
          databaseUpdateService.execute("UPDATE t_fibu_auftrag_position SET art = 'WARTUNG' WHERE art = 'HOT_FIX'");
        }
        if (databaseUpdateService.doesTableAttributeExist("T_FIBU_PROJEKT", "projectmanager_fk") == false
            || databaseUpdateService.doesTableAttributeExist("T_FIBU_PROJEKT", "headofbusinessmanager_fk") == false
            || databaseUpdateService.doesTableAttributeExist("T_FIBU_PROJEKT", "salesmanager_fk") == false
            || databaseUpdateService.doesTableAttributeExist("t_fibu_auftrag", "projectmanager_fk") == false
            || databaseUpdateService.doesTableAttributeExist("t_fibu_auftrag", "headofbusinessmanager_fk") == false
            || databaseUpdateService.doesTableAttributeExist("t_fibu_auftrag", "salesmanager_fk") == false) {
          //Updating the schema
          initDatabaseDao.updateSchema();
        }

        if (doesAuftragPotenzialNeedsUpdate()) {
          databaseUpdateService.replaceTableCellStrings(AuftragDO.class, AUFTRAG_TABLE_COL_NAME, AUFTRAG_OLD_STATUS_POTENZIAL, AUFTRAG_NEW_STATUS_POTENZIAL);
        }
        if (doesAuftragPosBeauftragtNeedsUpdate()) {
          databaseUpdateService.replaceTableCellStrings(AuftragsPositionDO.class, AUFTRAG_POS_TABLE_COL_NAME, AUFTRAG_POS_OLD_STATUS_BEAUFTRAGT,
              AUFTRAG_POS_NEW_STATUS_BEAUFTRAGT);
        }
        if (doesAuftragPosAbgelehntNeedsUpdate()) {
          databaseUpdateService
              .replaceTableCellStrings(AuftragsPositionDO.class, AUFTRAG_POS_TABLE_COL_NAME, AUFTRAG_POS_OLD_STATUS_ABGELEHNT,
                  AUFTRAG_POS_NEW_STATUS_ABGELEHNT);
        }

        return UpdateRunningStatus.DONE;
      }

      private boolean isUpdateFibuAuftragPositionRequired()
      {
        // new field does not exist
        if (databaseUpdateService.doesTableAttributeExist("t_fibu_auftrag_position", "paymentType") == false)
          return true;

        // old values in art field
        if (databaseUpdateService.doesTableRowExists(AuftragsPositionDO.class, "art", "FESTPREISPAKET", true) ||
            databaseUpdateService.doesTableRowExists(AuftragsPositionDO.class, "art", "TIME_AND_MATERIALS", true) ||
            databaseUpdateService.doesTableRowExists(AuftragsPositionDO.class, "art", "HOT_FIX", true))
          return true;

        return false;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.6.1
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.6.1", "2016-12-23", "Add probability of occurrence to order book.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.6.1");
        if (databaseUpdateService.doesTableAttributeExist("t_fibu_auftrag", "probability_of_occurrence") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doesTableAttributeExist("t_fibu_auftrag", "probability_of_occurrence") == false) {
          //Updating the schema
          initDatabaseDao.updateSchema();
        }
        return UpdateRunningStatus.DONE;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.6.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.6.0", "2016-12-14",
        "Add new visitorbook tables. Add table for vacation." +
            "Add new column in user table [lastWlanPasswordChange]. " +
            "Add new columns in order table [erfassungsDatum, entscheidungsDatum].")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.6.0");
        if (databaseUpdateService.doesTableExist("T_EMPLOYEE_VACATION") == false
            || databaseUpdateService.doesTableRowExists("T_CONFIGURATION", "PARAMETER", "hr.emailaddress",
            true) == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        } else if (
            databaseUpdateService.doTablesExist(VisitorbookDO.class, VisitorbookTimedDO.class, VisitorbookTimedAttrDO.class, VisitorbookTimedAttrDataDO.class,
                VisitorbookTimedAttrWithDataDO.class) == false || databaseUpdateService.doesGroupExists(ProjectForgeGroup.ORGA_TEAM) == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        } else if (databaseUpdateService.doTableAttributesExist(PFUserDO.class, "lastWlanPasswordChange") == false
            || databaseUpdateService.doTableAttributesExist(AuftragDO.class, "erfassungsDatum", "entscheidungsDatum") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if ((databaseUpdateService.doesTableExist("T_EMPLOYEE_VACATION") == false) || (databaseUpdateService
            .doTablesExist(VisitorbookDO.class, VisitorbookTimedDO.class, VisitorbookTimedAttrDO.class, VisitorbookTimedAttrDataDO.class,
                VisitorbookTimedAttrWithDataDO.class) == false)
            || databaseUpdateService.doTableAttributesExist(PFUserDO.class, "lastWlanPasswordChange") == false
            || databaseUpdateService.doTableAttributesExist(AuftragDO.class, "erfassungsDatum", "entscheidungsDatum") == false) {
          //Updating the schema
          initDatabaseDao.updateSchema();
        }
        if (databaseUpdateService.doesTableRowExists("T_CONFIGURATION", "PARAMETER", "hr.emailaddress",
            true) == false) {
          final PfEmgrFactory emf = applicationContext.getBean(PfEmgrFactory.class);
          emf.runInTrans(emgr -> {
            ConfigurationDO confEntry = new ConfigurationDO();
            confEntry.setConfigurationType(ConfigurationType.STRING);
            confEntry.setGlobal(false);
            confEntry.setParameter("hr.emailaddress");
            confEntry.setStringValue("hr@management.de");
            emgr.insert(confEntry);
            return UpdateRunningStatus.DONE;
          });
        }
        if (databaseUpdateService.doesGroupExists(ProjectForgeGroup.ORGA_TEAM) == false) {
          GroupDao groupDao = applicationContext.getBean(GroupDao.class);
          GroupDO orgaGroup = new GroupDO();
          orgaGroup.setName(ProjectForgeGroup.ORGA_TEAM.getName());
          groupDao.internalSave(orgaGroup);
        }

        return UpdateRunningStatus.DONE;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.5.2
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.5.2", "2016-11-24",
        "Add creator to team event.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.5.2");
        if (databaseUpdateService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT", "team_event_fk_creator") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT", "team_event_fk_creator") == false) {
          //Updating the schema
          initDatabaseDao.updateSchema();
        }
        return UpdateRunningStatus.DONE;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.4.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.4.0", "2016-10-12",
        "Move employee status to new timeable attribute.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.4.0");
        // ensure that the tenant exists, otherwise the following statements will fail with an SQL exception
        if (!databaseUpdateService.doTablesExist(TenantDO.class) || databaseUpdateService.internalIsTableEmpty("T_TENANT")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        final EmployeeDao employeeDao = applicationContext.getBean(EmployeeDao.class);
        final boolean anyEmployeeWithAnOldStatusExists = databaseUpdateService.doTablesExist(EmployeeDO.class) &&
            employeeDao
                .internalLoadAll()
                .stream()
                .filter(e -> !e.isDeleted())
                .anyMatch(e -> e.getStatus() != null);

        final int employeeStatusGroupEntriesCount = databaseUpdateService
            .countTimeableAttrGroupEntries(EmployeeTimedDO.class, InternalAttrSchemaConstants.EMPLOYEE_STATUS_GROUP_NAME);

        if (anyEmployeeWithAnOldStatusExists && employeeStatusGroupEntriesCount <= 0) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        } else {
          return UpdatePreCheckStatus.ALREADY_UPDATED;
        }
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        migrateEmployeeStatusToAttr();

        return UpdateRunningStatus.DONE;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.3.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.3.0", "2016-08-31",
        "Add column to attendee data table. Alter table column for ssh-key. Add HR group.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.3.0");
        if (databaseUpdateService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT_ATTENDEE", "address_id") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        } else if (databaseUpdateService.getDatabaseTableColumnLenght(PFUserDO.class, "ssh_public_key") < 4096) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        } else if (databaseUpdateService.doesGroupExists(ProjectForgeGroup.HR_GROUP) == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        } else if (databaseUpdateService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT", "uid") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        } else {
          return UpdatePreCheckStatus.ALREADY_UPDATED;
        }
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT_ATTENDEE", "address_id") == false
            || databaseUpdateService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT", "uid") == false) {
          // fix unique constraint error
          uniqueConstraintWorkaround(databaseUpdateService, emf);

          // Updating the schema
          initDatabaseDao.updateSchema();
        }

        if (databaseUpdateService.getDatabaseTableColumnLenght(PFUserDO.class, "ssh_public_key") < 4096) {
          final Table userTable = new Table(PFUserDO.class);
          databaseUpdateService.alterTableColumnVarCharLength(userTable.getName(), "SSH_PUBLIC_KEY", 4096);
        }

        if (databaseUpdateService.doesGroupExists(ProjectForgeGroup.HR_GROUP) == false) {
          emf.runInTrans(emgr -> {
            GroupDO hrGroup = new GroupDO();
            hrGroup.setName("PF_HR");
            hrGroup.setDescription("Users for having full access to the companies hr.");
            hrGroup.setCreated();
            hrGroup.setTenant(applicationContext.getBean(TenantService.class).getDefaultTenant());

            final Set<PFUserDO> usersToAddToHrGroup = new HashSet<>();

            final List<UserRightDO> employeeRights = emgr.selectAttached(UserRightDO.class,
                "SELECT r FROM UserRightDO r WHERE r.rightIdString = :rightId",
                "rightId",
                "FIBU_EMPLOYEE");
            employeeRights.forEach(sr -> {
              sr.setRightIdString("HR_EMPLOYEE");
              usersToAddToHrGroup.add(sr.getUser());
              emgr.update(sr);
            });

            final List<UserRightDO> salaryRights = emgr.selectAttached(UserRightDO.class,
                "SELECT r FROM UserRightDO r WHERE r.rightIdString = :rightId",
                "rightId",
                "FIBU_EMPLOYEE_SALARY");

            salaryRights.forEach(sr -> {
              sr.setRightIdString("HR_EMPLOYEE_SALARY");
              usersToAddToHrGroup.add(sr.getUser());
              emgr.update(sr);
            });

            usersToAddToHrGroup.forEach(hrGroup::addUser);

            emgr.insert(hrGroup);
            return hrGroup;
          });
        }

        return UpdateRunningStatus.DONE;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.1.1
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.1.1", "2016-07-27",
        "Changed timezone of starttime of the configurable attributes. Add uid to attendee.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.1.1");
        if (databaseUpdateService.doTablesExist(EmployeeTimedDO.class) == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        if (databaseUpdateService.isTableEmpty(EmployeeTimedDO.class)) {
          return UpdatePreCheckStatus.ALREADY_UPDATED;
        }

        final boolean timeFieldsOfAllEmployeeTimedDOsStartTimeAreZero = emf
            .runWoTrans(emgr -> emgr.selectAllAttached(EmployeeTimedDO.class)
                .stream()
                .map(EmployeeTimedDO::getStartTime)
                .map(DateHelper::convertDateToLocalDateTimeInUTC)
                .map(localDateTime -> localDateTime.get(ChronoField.SECOND_OF_DAY))
                .allMatch(seconds -> seconds == 0));

        return timeFieldsOfAllEmployeeTimedDOsStartTimeAreZero ? UpdatePreCheckStatus.ALREADY_UPDATED
            : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doTablesExist(EmployeeTimedDO.class) == false) {
          // fix unique constraint error
          uniqueConstraintWorkaround(databaseUpdateService, emf);
          // Updating the schema
          initDatabaseDao.updateSchema();
        }

        return emf.runInTrans(emgr -> {
          emgr.selectAllAttached(EmployeeTimedDO.class)
              .forEach(this::normalizeStartTime);

          return UpdateRunningStatus.DONE;
        });
      }

      private void normalizeStartTime(final TimeableRow entity)
      {
        final Date oldStartTime = entity.getStartTime();
        final Date newStartTime = DateHelper.convertMidnightDateToUTC(oldStartTime);
        entity.setStartTime(newStartTime);
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.1.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.1.0", "2016-07-14",
        "Adds several columns to employee table.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.1.0");
        if (databaseUpdateService.doTableAttributesExist(EmployeeDO.class, "staffNumber") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        // fix unique constraint error
        uniqueConstraintWorkaround(databaseUpdateService, emf);
        // Updating the schema
        initDatabaseDao.updateSchema();

        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 6.0.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.0.0", "2016-04-01",
        "Adds tenant table, tenant_id to all entities for multi-tenancy. Adds new history tables. Adds attr table for address. Adds t_configuration.is_global, t_pf_user.super_admin.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.0.0");
        if (databaseUpdateService.doTablesExist(TenantDO.class) == false
            || databaseUpdateService.internalIsTableEmpty("t_tenant") == true ||
            databaseUpdateService.doTableAttributesExist(ConfigurationDO.class, "global") == false ||
            databaseUpdateService.doTableAttributesExist(PFUserDO.class, "superAdmin") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @SuppressWarnings({ "unchecked", "rawtypes" })
      @Override
      public UpdateRunningStatus runUpdate()
      {
        // fix unique constraint error
        uniqueConstraintWorkaround(databaseUpdateService, emf);

        // drop foreign keys for all known tables
        // ------------------------------------------------------------------------------------------------------------------------
        int count = databaseUpdateService.dropForeignKeys();
        log.info(String.format("%s foreign keys are dropped due to the new hibernate naming schema", count));
        // ------------------------------------------------------------------------------------------------------------------------

        // Updating the schema
        initDatabaseDao.updateSchema();

        // init default tenant
        TenantDO defaultTenant;
        if (databaseUpdateService.internalIsTableEmpty("t_tenant") == true) {
          try {
            defaultTenant = initDatabaseDao.insertDefaultTenant();
          } catch (Exception e) {
            e.printStackTrace();
            return UpdateRunningStatus.FAILED;
          }
        } else {
          defaultTenant = tenantDao.getDefaultTenant();
        }

        //Insert default tenant on every entity
        log.info("Start adding default tenant to entities.");
        List<EntityMetadata> entities = emf.getMetadataRepository().getTableEntities();
        Collections.reverse(entities);
        for (EntityMetadata entityClass : entities) {
          if (AbstractBaseDO.class.isAssignableFrom(entityClass.getJavaType())) {
            try {
              log.info("Set tenant id for entities of type: " + entityClass.getJavaType());
              emf.tx().go(emgr -> {
                Class<? extends AbstractBaseDO> entity = (Class<? extends AbstractBaseDO>) entityClass.getJavaType();
                CriteriaUpdate<? extends AbstractBaseDO> cu = CriteriaUpdate.createUpdate(entity);
                cu.set("tenant", defaultTenant);
                emgr.update(cu);
                return null;
              });
            } catch (Exception e) {
              log.error("Failed to update default tenant for entities of type: " + entityClass.getJavaType());
            }
          }
          if (UserXmlPreferencesDO.class.isAssignableFrom(entityClass.getJavaType())) {
            try {
              emf.runInTrans(emgr -> {
                log.info("Set tenant id for entities of type: " + UserXmlPreferencesDO.class.getClass());
                CriteriaUpdate<UserXmlPreferencesDO> cu = CriteriaUpdate.createUpdate(UserXmlPreferencesDO.class);
                cu.set("tenant", defaultTenant);
                emgr.update(cu);
                return null;
              });
            } catch (Exception e) {
              log.error("Failed to update default tenant for user xml prefs.");
            }
          }
        }
        log.info("Finished adding default tenant to entities.");

        // assign default tenant to each user
        log.info("Start assigning users to default tenant.");
        try {
          emf.tx().go(emgr -> {
            TenantDO attachedDefaultTenant = emgr.selectByPkAttached(TenantDO.class, defaultTenant.getId());
            List<PFUserDO> users = emgr.selectAttached(PFUserDO.class, "select u from PFUserDO u");
            for (PFUserDO user : users) {
              log.info("Assign user with id: " + user.getId() + " to default tenant.");
              attachedDefaultTenant.getAssignedUsers().add(user);
              emgr.update(attachedDefaultTenant);
            }
            return null;
          });
        } catch (Exception e) {
          log.error("Failed to assign users to default tenant.");
        }
        log.info("Finished assigning users to default tenant.");

        //History migration
        log.info("Start migrating history data.");
        HistoryMigrateService ms = applicationContext.getBean(HistoryMigrateService.class);
        long start = System.currentTimeMillis();
        try {
          ms.migrate();
        } catch (Exception ex) {
          log.error("Error while migrating history data", ex);
        }
        log.info("History Migration took: " + (System.currentTimeMillis() - start) / 1000 + " sec");
        log.info("Finished migrating history data.");

        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // /////////////////////////////////////////////////////////////////
    // 5.5
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(
        CORE_REGION_ID,
        "5.5",
        "2014-08-11",
        "Adds t_group.ldap_values, t_fibu_auftrag_position.period_of_performance_type, t_fibu_auftrag_position.mode_of_payment_type, t_fibu_payment_schedule, t_fibu_auftrag.period_of_performance_{begin|end}, length of t_address.public_key increased.")
    {

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        if (RESTART_RQUIRED.equals("v5.5")) {
          return UpdatePreCheckStatus.RESTART_REQUIRED;
        }
        log.info("Running pre-check for ProjectForge version 5.5");
        if (databaseUpdateService.doTableAttributesExist(EmployeeDO.class, "weeklyWorkingHours") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        if (databaseUpdateService.doTableAttributesExist(GroupDO.class, "ldapValues") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        if (databaseUpdateService.doTableAttributesExist(AuftragsPositionDO.class, "periodOfPerformanceType",
            "modeOfPaymentType") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        if (databaseUpdateService.doTableAttributesExist(AuftragDO.class, "periodOfPerformanceBegin",
            "periodOfPerformanceEnd") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        if (databaseUpdateService.doTablesExist(PaymentScheduleDO.class) == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doTableAttributesExist(EmployeeDO.class, "weeklyWorkingHours") == false) {
          // No length check available so assume enlargement if ldapValues doesn't yet exist:
          final Table addressTable = new Table(AddressDO.class);
          databaseUpdateService.alterTableColumnVarCharLength(addressTable.getName(), "public_key", 20000);

          // TODO HIBERNATE5 no longer supported
          //          final Table propertyDeltaTable = new Table(PropertyDelta.class);
          //          dao.alterTableColumnVarCharLength(propertyDeltaTable.getName(), "old_value", 20000);
          //          dao.alterTableColumnVarCharLength(propertyDeltaTable.getName(), "new_value", 20000);

          final Table employeeTable = new Table(EmployeeDO.class);
          databaseUpdateService.renameTableAttribute(employeeTable.getName(), "wochenstunden", "old_weekly_working_hours");
          databaseUpdateService.addTableAttributes(EmployeeDO.class, "weeklyWorkingHours");
          final List<DatabaseResultRow> rows = databaseUpdateService
              .query("select pk, old_weekly_working_hours from t_fibu_employee");
          if (rows != null) {
            for (final DatabaseResultRow row : rows) {
              final Integer pk = (Integer) row.getEntry("pk").getValue();
              final Integer oldWeeklyWorkingHours = (Integer) row.getEntry("old_weekly_working_hours").getValue();
              if (oldWeeklyWorkingHours == null) {
                continue;
              }
              databaseUpdateService.update("update t_fibu_employee set weekly_working_hours=? where pk=?",
                  new BigDecimal(oldWeeklyWorkingHours), pk);
            }
          }
        }
        if (databaseUpdateService.doTableAttributesExist(GroupDO.class, "ldapValues") == false) {
          databaseUpdateService.addTableAttributes(GroupDO.class, "ldapValues");
        }
        if (databaseUpdateService.doTableAttributesExist(AuftragsPositionDO.class, "periodOfPerformanceType",
            "modeOfPaymentType") == false) {
          databaseUpdateService.addTableAttributes(AuftragsPositionDO.class, "modeOfPaymentType");
          databaseUpdateService.addTableAttributes(AuftragsPositionDO.class, "periodOfPerformanceType");
        }
        if (databaseUpdateService.doTableAttributesExist(AuftragDO.class, "periodOfPerformanceBegin",
            "periodOfPerformanceEnd") == false) {
          databaseUpdateService.addTableAttributes(AuftragDO.class, "periodOfPerformanceBegin");
          databaseUpdateService.addTableAttributes(AuftragDO.class, "periodOfPerformanceEnd");
        }
        if (databaseUpdateService.doTablesExist(PaymentScheduleDO.class) == false) {
          new SchemaGenerator(databaseUpdateService).add(TenantDO.class).createSchema();
          new SchemaGenerator(databaseUpdateService).add(PaymentScheduleDO.class).createSchema();
          databaseUpdateService.createMissingIndices();
        }

        RESTART_RQUIRED = "v5.5";
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 5.3
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "5.3", "2013-11-24",
        "Adds t_pf_user.last_password_change, t_pf_user.password_salt.")
    {

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        if (databaseUpdateService.doTableAttributesExist(PFUserDO.class, "lastPasswordChange", "passwordSalt") == false) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doTableAttributesExist(PFUserDO.class, "lastPasswordChange", "passwordSalt") == false) {
          databaseUpdateService.addTableAttributes(PFUserDO.class, "lastPasswordChange", "passwordSalt");
        }
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 5.2
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(
        CORE_REGION_ID,
        "5.2",
        "2013-05-13",
        "Adds t_fibu_auftrag_position.time_of_performance_{start|end}, t_script.file{_name} and changes type of t_script.script{_backup} to byte[].")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        if (databaseUpdateService.doTableAttributesExist(ScriptDO.class, "file", "filename") == true
            && databaseUpdateService.doTableAttributesExist(AuftragsPositionDO.class, "periodOfPerformanceBegin", "periodOfPerformanceEnd") == true) {
          return UpdatePreCheckStatus.ALREADY_UPDATED;
        }
        return UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doTableAttributesExist(ScriptDO.class, "file", "filename") == false) {
          databaseUpdateService.addTableAttributes(ScriptDO.class, "file", "filename");
          final Table scriptTable = new Table(ScriptDO.class);
          databaseUpdateService.renameTableAttribute(scriptTable.getName(), "script", "old_script");
          databaseUpdateService.renameTableAttribute(scriptTable.getName(), "scriptbackup", "old_script_backup");
          databaseUpdateService.addTableAttributes(ScriptDO.class, "script", "scriptBackup");
          final List<DatabaseResultRow> rows = databaseUpdateService
              .query("select pk, old_script, old_script_backup from t_script");
          if (rows != null) {
            for (final DatabaseResultRow row : rows) {
              final Integer pk = (Integer) row.getEntry("pk").getValue();
              final String oldScript = (String) row.getEntry("old_script").getValue();
              final String oldScriptBackup = (String) row.getEntry("old_script_backup").getValue();
              final ScriptDO script = new ScriptDO();
              script.setScriptAsString(oldScript);
              script.setScriptBackupAsString(oldScriptBackup);
              databaseUpdateService.update("update t_script set script=?, script_backup=? where pk=?", script.getScript(),
                  script.getScriptBackup(), pk);
            }
          }
        }
        if (databaseUpdateService.doTableAttributesExist(AuftragsPositionDO.class, "periodOfPerformanceBegin",
            "periodOfPerformanceEnd") == false) {
          databaseUpdateService.addTableAttributes(AuftragsPositionDO.class, "periodOfPerformanceBegin",
              "periodOfPerformanceEnd");
        }
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 5.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, VERSION_5_0, "2013-02-15",
        "Adds t_fibu_rechnung.konto, t_pf_user.ssh_public_key, fixes contract.IN_PROGRES -> contract.IN_PROGRESS")
    {
      final Table rechnungTable = new Table(RechnungDO.class);

      final Table userTable = new Table(PFUserDO.class);

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        int entriesToMigrate = 0;
        if (databaseUpdateService.isVersionUpdated(CORE_REGION_ID, VERSION_5_0) == false) {
          entriesToMigrate = databaseUpdateService.queryForInt("select count(*) from t_contract where status='IN_PROGRES'");
        }
        return (databaseUpdateService.doTableAttributesExist(rechnungTable, "konto")
            && databaseUpdateService.doTableAttributesExist(userTable, "sshPublicKey")
            && entriesToMigrate == 0)
            ? UpdatePreCheckStatus.ALREADY_UPDATED : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doTableAttributesExist(rechnungTable, "konto") == false) {
          databaseUpdateService.addTableAttributes(rechnungTable, new TableAttribute(RechnungDO.class, "konto"));
        }
        if (databaseUpdateService.doTableAttributesExist(userTable, "sshPublicKey") == false) {
          databaseUpdateService.addTableAttributes(userTable, new TableAttribute(PFUserDO.class, "sshPublicKey"));
        }
        final int entriesToMigrate = databaseUpdateService
            .queryForInt("select count(*) from t_contract where status='IN_PROGRES'");
        if (entriesToMigrate > 0) {
          databaseUpdateService.execute("update t_contract set status='IN_PROGRESS' where status='IN_PROGRES'", true);
        }
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 4.3.1
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "4.3.1", "2013-01-29", "Adds t_fibu_projekt.konto")
    {
      final Table projektTable = new Table(ProjektDO.class);

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        return databaseUpdateService.doTableAttributesExist(projektTable, "konto") == true //
            ? UpdatePreCheckStatus.ALREADY_UPDATED
            : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doTableAttributesExist(projektTable, "konto") == false) {
          databaseUpdateService.addTableAttributes(projektTable, new TableAttribute(ProjektDO.class, "konto"));
        }
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 4.2
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(
        CORE_REGION_ID,
        "4.2",
        "2012-08-09",
        "Adds t_pf_user.authenticationToken|local_user|restricted_user|deactivated|ldap_values, t_group.local_group, t_fibu_rechnung|eingangsrechnung|auftrag(=incoming and outgoing invoice|order).ui_status_as_xml")
    {
      final Table userTable = new Table(PFUserDO.class);

      final Table groupTable = new Table(GroupDO.class);

      final Table outgoingInvoiceTable = new Table(RechnungDO.class);

      final Table incomingInvoiceTable = new Table(EingangsrechnungDO.class);

      final Table orderTable = new Table(AuftragDO.class);

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        return databaseUpdateService.doTableAttributesExist(userTable, "authenticationToken", "localUser", "restrictedUser",
            "deactivated", "ldapValues") == true //
            && databaseUpdateService.doTableAttributesExist(groupTable, "localGroup") == true
            // , "nestedGroupsAllowed", "nestedGroupIds") == true //
            && databaseUpdateService.doTableAttributesExist(outgoingInvoiceTable, "uiStatusAsXml") == true //
            && databaseUpdateService.doTableAttributesExist(incomingInvoiceTable, "uiStatusAsXml") == true //
            && databaseUpdateService.doTableAttributesExist(orderTable, "uiStatusAsXml") == true //
            ? UpdatePreCheckStatus.ALREADY_UPDATED : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doTableAttributesExist(userTable, "authenticationToken") == false) {
          databaseUpdateService.addTableAttributes(userTable, new TableAttribute(PFUserDO.class, "authenticationToken"));
        }
        if (databaseUpdateService.doTableAttributesExist(userTable, "localUser") == false) {
          databaseUpdateService.addTableAttributes(userTable,
              new TableAttribute(PFUserDO.class, "localUser").setDefaultValue("false"));
        }
        if (databaseUpdateService.doTableAttributesExist(userTable, "restrictedUser") == false) {
          databaseUpdateService.addTableAttributes(userTable,
              new TableAttribute(PFUserDO.class, "restrictedUser").setDefaultValue("false"));
        }
        if (databaseUpdateService.doTableAttributesExist(userTable, "deactivated") == false) {
          databaseUpdateService.addTableAttributes(userTable,
              new TableAttribute(PFUserDO.class, "deactivated").setDefaultValue("false"));
        }
        if (databaseUpdateService.doTableAttributesExist(userTable, "ldapValues") == false) {
          databaseUpdateService.addTableAttributes(userTable, new TableAttribute(PFUserDO.class, "ldapValues"));
        }
        if (databaseUpdateService.doTableAttributesExist(groupTable, "localGroup") == false) {
          databaseUpdateService.addTableAttributes(groupTable,
              new TableAttribute(GroupDO.class, "localGroup").setDefaultValue("false"));
        }
        // if (dao.doesTableAttributesExist(groupTable, "nestedGroupsAllowed") == false) {
        // dao.addTableAttributes(groupTable, new TableAttribute(GroupDO.class, "nestedGroupsAllowed").setDefaultValue("true"));
        // }
        // if (dao.doesTableAttributesExist(groupTable, "nestedGroupIds") == false) {
        // dao.addTableAttributes(groupTable, new TableAttribute(GroupDO.class, "nestedGroupIds"));
        // }
        if (databaseUpdateService.doTableAttributesExist(outgoingInvoiceTable, "uiStatusAsXml") == false) {
          databaseUpdateService.addTableAttributes(outgoingInvoiceTable,
              new TableAttribute(RechnungDO.class, "uiStatusAsXml"));
        }
        if (databaseUpdateService.doTableAttributesExist(incomingInvoiceTable, "uiStatusAsXml") == false) {
          databaseUpdateService.addTableAttributes(incomingInvoiceTable,
              new TableAttribute(EingangsrechnungDO.class, "uiStatusAsXml"));
        }
        if (databaseUpdateService.doTableAttributesExist(orderTable, "uiStatusAsXml") == false) {
          databaseUpdateService.addTableAttributes(orderTable, new TableAttribute(AuftragDO.class, "uiStatusAsXml"));
        }
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 4.1
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "4.1", "2012-04-21",
        "Adds t_pf_user.first_day_of_week and t_pf_user.hr_planning.")
    {
      final Table userTable = new Table(PFUserDO.class);

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        return databaseUpdateService.doTableAttributesExist(userTable, "firstDayOfWeek", "hrPlanning") == true //
            ? UpdatePreCheckStatus.ALREADY_UPDATED
            : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doTableAttributesExist(userTable, "firstDayOfWeek") == false) {
          databaseUpdateService.addTableAttributes(userTable, new TableAttribute(PFUserDO.class, "firstDayOfWeek"));
        }
        if (databaseUpdateService.doTableAttributesExist(userTable, "hrPlanning") == false) {
          databaseUpdateService.addTableAttributes(userTable,
              new TableAttribute(PFUserDO.class, "hrPlanning").setDefaultValue("true"));
        }
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 4.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "4.0", "2012-04-18",
        "Adds 6th parameter to t_script and payment_type to t_fibu_eingangsrechnung.")
    {
      final Table scriptTable = new Table(ScriptDO.class);

      final Table eingangsrechnungTable = new Table(EingangsrechnungDO.class);

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        return databaseUpdateService.doTableAttributesExist(scriptTable, "parameter6Name", "parameter6Type") == true //
            && databaseUpdateService.doTableAttributesExist(eingangsrechnungTable, "paymentType") == true //
            ? UpdatePreCheckStatus.ALREADY_UPDATED : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (databaseUpdateService.doTableAttributesExist(scriptTable, "parameter6Name") == false) {
          databaseUpdateService.addTableAttributes(scriptTable, new TableAttribute(ScriptDO.class, "parameter6Name"));
        }
        if (databaseUpdateService.doTableAttributesExist(scriptTable, "parameter6Type") == false) {
          databaseUpdateService.addTableAttributes(scriptTable, new TableAttribute(ScriptDO.class, "parameter6Type"));
        }
        if (databaseUpdateService.doTableAttributesExist(eingangsrechnungTable, "paymentType") == false) {
          databaseUpdateService.addTableAttributes(eingangsrechnungTable,
              new TableAttribute(EingangsrechnungDO.class, "paymentType"));
        }
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 3.6.2
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(
        CORE_REGION_ID,
        "3.6.1.3",
        "2011-12-05",
        "Adds columns t_kunde.konto_id, t_fibu_eingangsrechnung.konto_id, t_konto.status, t_task.protection_of_privacy and t_address.communication_language.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        final Table kundeTable = new Table(KundeDO.class);
        final Table eingangsrechnungTable = new Table(EingangsrechnungDO.class);
        final Table kontoTable = new Table(KontoDO.class);
        final Table taskTable = new Table(TaskDO.class);
        final Table addressTable = new Table(AddressDO.class);
        return databaseUpdateService.doTableAttributesExist(kundeTable, "konto") == true //
            && databaseUpdateService.doTableAttributesExist(eingangsrechnungTable, "konto") == true //
            && databaseUpdateService.doTableAttributesExist(kontoTable, "status") == true //
            && databaseUpdateService.doTableAttributesExist(addressTable, "communicationLanguage") == true //
            && databaseUpdateService.doTableAttributesExist(taskTable, "protectionOfPrivacy") //
            ? UpdatePreCheckStatus.ALREADY_UPDATED : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        final Table kundeTable = new Table(KundeDO.class);
        if (databaseUpdateService.doTableAttributesExist(kundeTable, "konto") == false) {
          databaseUpdateService.addTableAttributes(kundeTable, new TableAttribute(KundeDO.class, "konto"));
        }
        final Table eingangsrechnungTable = new Table(EingangsrechnungDO.class);
        if (databaseUpdateService.doTableAttributesExist(eingangsrechnungTable, "konto") == false) {
          databaseUpdateService.addTableAttributes(eingangsrechnungTable,
              new TableAttribute(EingangsrechnungDO.class, "konto"));
        }
        final Table kontoTable = new Table(KontoDO.class);
        if (databaseUpdateService.doTableAttributesExist(kontoTable, "status") == false) {
          databaseUpdateService.addTableAttributes(kontoTable, new TableAttribute(KontoDO.class, "status"));
        }
        final Table taskTable = new Table(TaskDO.class);
        if (databaseUpdateService.doTableAttributesExist(taskTable, "protectionOfPrivacy") == false) {
          databaseUpdateService.addTableAttributes(taskTable,
              new TableAttribute(TaskDO.class, "protectionOfPrivacy").setDefaultValue("false"));
        }
        final Table addressTable = new Table(AddressDO.class);
        if (databaseUpdateService.doTableAttributesExist(addressTable, "communicationLanguage") == false) {
          databaseUpdateService.addTableAttributes(addressTable,
              new TableAttribute(AddressDO.class, "communicationLanguage"));
        }
        databaseUpdateService.createMissingIndices();
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 3.5.4
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "3.5.4", "2011-02-24",
        "Adds table t_database_update. Adds attribute (excel_)date_format, hour_format_24 to table t_pf_user.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        final Table dbUpdateTable = new Table(DatabaseUpdateDO.class);
        final Table userTable = new Table(PFUserDO.class);
        return databaseUpdateService.doExist(dbUpdateTable) == true
            && databaseUpdateService.doTableAttributesExist(userTable, "dateFormat", "excelDateFormat",
            "timeNotation") == true //
            ? UpdatePreCheckStatus.ALREADY_UPDATED : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        final Table dbUpdateTable = new Table(DatabaseUpdateDO.class);
        final Table userTable = new Table(PFUserDO.class);
        dbUpdateTable.addAttributes("updateDate", "regionId", "versionString", "executionResult", "executedBy",
            "description");
        databaseUpdateService.createTable(dbUpdateTable);
        databaseUpdateService.addTableAttributes(userTable, new TableAttribute(PFUserDO.class, "dateFormat"));
        databaseUpdateService.addTableAttributes(userTable, new TableAttribute(PFUserDO.class, "excelDateFormat"));
        databaseUpdateService.addTableAttributes(userTable, new TableAttribute(PFUserDO.class, "timeNotation"));
        databaseUpdateService.createMissingIndices();
        TenantRegistryMap.getInstance().setAllUserGroupCachesAsExpired();
        //TODO: Lösung finden!!!
        //Registry.instance().getUserCache().setExpired();
        return UpdateRunningStatus.DONE;
      }
    });
    return list;
  }

  private static void uniqueConstraintWorkaround(final DatabaseUpdateService dus, final PfEmgrFactory emf)
  {
    EntityMetadata pce;

    try {
      pce = emf.getMetadataRepository().getEntityMetaDataBySimpleClassName("TeamEventDO");
    } catch (JpaMetadataEntityNotFoundException e) {
      log.error("No JPA class found for TeamEventDO");
      pce = null;
    }

    if (dus.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT", "uid") == false && pce != null) {
      // required workaround, because null values are not accepted
      final String type = dus.getAttribute(pce.getJavaType(), "uid");
      final String command1 = String.format("ALTER TABLE T_PLUGIN_CALENDAR_EVENT ADD COLUMN UID %s DEFAULT 'default value'", type);

      dus.execute(command1);
      dus.execute("ALTER TABLE T_PLUGIN_CALENDAR_EVENT ALTER COLUMN UID SET NOT NULL");
      dus.execute("ALTER TABLE T_PLUGIN_CALENDAR_EVENT ALTER COLUMN UID DROP DEFAULT");
    }
  }

  public static void migrateEmployeeStatusToAttr()
  {
    final EmployeeService employeeService = applicationContext.getBean(EmployeeService.class);
    final EmployeeDao employeeDao = applicationContext.getBean(EmployeeDao.class);

    final List<EmployeeDO> employees = employeeDao.internalLoadAll();
    employees.forEach(employee -> {
      final EmployeeStatus status = employee.getStatus();
      if (status != null) {
        final EmployeeTimedDO newAttrRow = employeeService.addNewTimeAttributeRow(employee, InternalAttrSchemaConstants.EMPLOYEE_STATUS_GROUP_NAME);
        newAttrRow.setStartTime(getDateForStatus(employee));
        newAttrRow.putAttribute(InternalAttrSchemaConstants.EMPLOYEE_STATUS_DESC_NAME, status.getI18nKey());
        employeeDao.internalUpdate(employee);
      }
    });
  }

  private static Date getDateForStatus(final EmployeeDO employee)
  {
    // At first try to find the last change of the employee status in the history ...
    final Optional<Date> lastChange = findLastChangeOfEmployeeStatusInHistory(employee);
    if (lastChange.isPresent()) {
      // convert date from UTC to current zone date
      final TimeZone utc = TimeZone.getTimeZone("UTC");
      final TimeZone currentTimeZone = Configuration.getInstance().getDefaultTimeZone();
      final Date dateInCurrentTimezone = DateHelper.convertDateIntoOtherTimezone(lastChange.get(), utc, currentTimeZone);
      return DateHelper.resetTimePartOfDate(dateInCurrentTimezone);
    }

    // ... if there is nothing in the history, then use the entrittsdatum ...
    final Date eintrittsDatum = employee.getEintrittsDatum();
    if (eintrittsDatum != null) {
      return DateHelper.convertMidnightDateToUTC(eintrittsDatum);
    }

    // ... if there is no eintrittsdatum, use the current date.
    return DateHelper.todayAtMidnight();
  }

  private static Optional<Date> findLastChangeOfEmployeeStatusInHistory(final EmployeeDO employee)
  {
    final Predicate<HistoryEntry> hasStatusChangeHistoryEntries = historyEntry ->
        ((HistoryEntry<?>) historyEntry)
            .getDiffEntries()
            .stream()
            .anyMatch(
                diffEntry -> diffEntry.getPropertyName().startsWith("status")
            );

    return HistoryBaseDaoAdapter
        .getHistoryEntries(employee)
        .stream()
        .filter(hasStatusChangeHistoryEntries)
        .map(HistoryEntry::getModifiedAt)
        .findFirst(); // the history entries are already sorted by date
  }
}
