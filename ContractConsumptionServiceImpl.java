package com.param.global.billing.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.param.entity.model.master.Tax;
import com.param.global.billing.dao.IContractConsumptionDao;
import com.param.global.common.GlobalCommonDateUtils;
import com.param.global.common.ICommonConstants;
import com.param.global.common.Response;
import com.param.global.dao.IDoctorConsultationServiceMapperDao;
import com.param.global.dao.IMPackageMasterDao;
import com.param.global.dao.IOrderDetailsMasterDao;
import com.param.global.dao.IUnitServiceMapperDao;
import com.param.global.dao.IUnitServiceTariffMasterDao;
import com.param.global.dto.CancelOrderDto;
import com.param.global.dto.ContractConsumptionDto;
import com.param.global.dto.DoctorConsultationServiceMapperDto;
import com.param.global.dto.OrderDetailsMasterDto;
import com.param.global.dto.OrderDetailsPackageMapperDto;
import com.param.global.dto.OrderDetailsPayeeMapperDto;
import com.param.global.dto.OrderMasterDto;
import com.param.global.dto.ServiceSearchResDto;
import com.param.global.dto.UnitServiceTariffMasterDto;
import com.param.global.opd.dao.IEncounterMasterDao;
import com.param.global.opd.service.IEncounterMasterService;
import com.param.global.service.IMPackageMasterService;
import com.param.global.service.OrderMasterServiceImpl;
import com.param.opd.encounter.dto.EncounterMasterDto;
import com.param.service.global.SessionService;
import com.param.utility.TimeZoneComponent;

import in.param.exception.ApplicationException;



@Service
@SuppressWarnings({"rawtypes","unchecked","unused"})
public class ContractConsumptionServiceImpl implements IContractConsumptionService,ICommonConstants{

	@Autowired
	IContractConsumptionDao iContractConsumptionDao; 
	
	@Autowired
	IEncounterMasterService iEncounterMasterService;
	
	@Autowired
	IMPackageMasterDao iMPackageMasterDao;
	
	@Autowired
	IUnitServiceTariffMasterDao iUnitServiceTariffMasterDao;
	
	@Autowired
	IUnitServiceMapperDao iUnitServiceMapperDao;
	
	@Autowired
	IOrderDetailsMasterDao iOrderDetailsMasterDao;
	
	@Autowired
	IEncounterMasterDao iEncounterMasterDao;
	
	@Autowired
	SessionService sessionService;
	
	@Autowired
	IDoctorConsultationServiceMapperDao iDoctorConsultationServiceMapperDao; 
	
	@Autowired
	IMPackageMasterService iMPackageMasterService; 
	
	@Transactional
	
	public Response consumeServiceFromContract(ContractConsumptionDto consumptionDto) {
		try{
			
			Character isBedService=iEncounterMasterDao.getIsBedChargesService(consumptionDto);
			if(isBedService==null)
			{
				isBedService='N';
			}
		    consumptionDto.setIsBedService(isBedService);
			if(consumptionDto.getVisitTypeId()==1)
				consumptionDto.setAdmissionId(null);
			if(consumptionDto.getVisitTypeId()==2 || consumptionDto.getVisitTypeId()==3 || consumptionDto.getVisitTypeId()==4)
				consumptionDto.setEncounterId(null);
			Double concession = 0.00;
			Double concessionPer = 0.00;
			Integer priorityId = 0;
			Character isRateEditable = 'N';
			Character isRateEditableService = 'N';
			Character isQuantityEditable='N';
			Integer count = 0;
			Double rate = null;
			Double serviceAmount = null;
			Double rateMinusConcession = null;
			double discountAmt = 0.00;
			Double rateDiff = null;
			Double contractRate = null;
			Double selfPayable = 0.00;
			Double creaditPayable = 0.00;
			Double preAuthAmount = 0.00;
			List<ContractConsumptionDto> serviceDetailslist = null;
			OrderDetailsPayeeMapperDto detailsPayeeMapperDto = null;
			List<OrderDetailsPayeeMapperDto> orderDetailsPayeeMapperDtosList = new LinkedList<>();
			Double taxAmt = 0.0;
			Double netAmt = 0.0;
			Double totalCreditPayable = 0.00;
			Double totalSelfPayable = 0.00;
			Double qty= 1.0;
			Integer specialityId = null;
			Integer subSpecialityId = null;
			Integer tariffId = null;
			ContractConsumptionDto finalDto = new ContractConsumptionDto();
			Integer serviceId = consumptionDto.getServiceId();
			if(consumptionDto.getProcedureId()!=null && consumptionDto.getProcedureId()>0 && consumptionDto.getIsBOM()!=null && consumptionDto.getIsBOM()=='Y')
			{
				consumptionDto.setServiceId(consumptionDto.getProcedureId());
			}
			ContractConsumptionDto patientClassDto = null;
			boolean gstApplicableForBillingBedCategory=iContractConsumptionDao.getGstApplicableForBillingBedCategory(consumptionDto.getUnitId());
			//<-----1. Check if an encounter or admission has any contract or not.
			if(consumptionDto.getContractId()!=null && consumptionDto.getContractId().intValue()>0)
			{
				//<-----1.1 Get the service rates from all the sponsors (list is fetched according to the highest priority)
				serviceDetailslist =  iContractConsumptionDao.getServiceDetailsByContractId(consumptionDto);
				for(ContractConsumptionDto resDto : serviceDetailslist)
				{	
					tariffId = resDto.getTariffId();
					resDto.setEncounterId(consumptionDto.getEncounterId());
					resDto.setAdmissionId(consumptionDto.getAdmissionId());
					specialityId = resDto.getSpecialityId();
					subSpecialityId = resDto.getSubSpecialityId();
					consumptionDto.setPaymentEntitlementId(resDto.getPaymentEntitlementId());
					if(consumptionDto.getPatientClassPercentage()!=null && consumptionDto.isPatientClassApplied()==false)
						concessionPer = consumptionDto.getPatientClassPercentage();
					
					else if((resDto.getIsDiscount()=='Y' && resDto.getIsSchemeDiscount()=='Y')||(resDto.getIsSchemeDiscount()=='N'))
					{
						patientClassDto = iContractConsumptionDao.getPatientClassAndPercentageByVisit(consumptionDto);
						
						if(patientClassDto !=null && patientClassDto.getMarkupStatus()!=null && patientClassDto.getMarkupStatus()==false && patientClassDto.getPatientClassPercentage()!=null && patientClassDto.getPatientClassPercentage()>0)
							concessionPer = patientClassDto.getPatientClassPercentage();
					}
					
					if(consumptionDto.getQuantity()!=null && consumptionDto.getQuantity()>1)
					{
						qty = consumptionDto.getQuantity();
						resDto.setSelfPayable(consumptionDto.getQuantity()*resDto.getSelfPayable());
						resDto.setCreaditPayable(consumptionDto.getQuantity()*resDto.getCreaditPayable());
						resDto.setFinalPrice(consumptionDto.getQuantity()*resDto.getFinalPrice());
					}
					
					if(resDto.getType()==1)	
					{
						if(consumptionDto.getConsultationAmount()!=null && consumptionDto.getConsultationAmount()>0)
						{
							rate = consumptionDto.getConsultationAmount()*qty;
							
							if(concessionPer>0.00)
								concession = rate*(concessionPer*0.01);
							else
								concession = 0.00;
							
							if(consumptionDto.getOrdDiscountPer()>0)
							{
								rateMinusConcession = (rate*qty) - concession;
								discountAmt = rateMinusConcession*(consumptionDto.getOrdDiscountPer()*0.01);
							}else{
								discountAmt = 0.00;
							}
							
							resDto.setServiceRate(rate);
							contractRate = (resDto.getFinalPrice()!=null ? resDto.getFinalPrice():resDto.getServiceRate());
							if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
							{
								List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
								for (ContractConsumptionDto gstList : gstDetailsList) {
									if(gstList.getIsGstApplicable()==true && rate>=gstList.getGstAmountRange())
									{
										resDto.setTaxId(0);
										resDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
										taxAmt = (rate-(concession+discountAmt)) * (gstList.getGstPercentage()*0.01);
									}
									else
									{
										resDto.setTaxId(0);
										resDto.setTaxPercentage(0.00);
										taxAmt =  0.00;
									}
								}
							}
							else
							{
								if(resDto.getTaxPercentage()!=null)
								{
									taxAmt = (rate-(concession+discountAmt)) * (resDto.getTaxPercentage()*0.01);
								}else{
									taxAmt =  0.00;
								}
							}
							resDto.setTaxAmt(taxAmt);
							rate = (rate-(concession+discountAmt))+taxAmt;
							resDto.setFinalPrice(rate);
							if(resDto.getIsTariffRateApplicable()=='Y')
							{
								resDto.setSelfPayable(resDto.getFinalPrice()*((resDto.getCoShare()!=null ? resDto.getCoShare() : 0.00 )*0.01));
								resDto.setSelfPayable(new Double(OrderMasterServiceImpl.df2.format(resDto.getSelfPayable())));
								resDto.setCreaditPayable(resDto.getFinalPrice()-resDto.getSelfPayable());
							}else {
								if(rate>=contractRate)
								{
									//contract amount will remain same the difference between service rate and contractRate
									rateDiff = rate-contractRate;
									selfPayable = resDto.getSelfPayable()+rateDiff;
								    creaditPayable = resDto.getCreaditPayable();
								}
								else
								{
									rateDiff = contractRate-rate;
									if(resDto.getCreaditPayable()>=rate)
									{
										selfPayable = 0.00;
									    creaditPayable = rate;
									}else{
										selfPayable = rateDiff;
									    creaditPayable = resDto.getCreaditPayable();
									}
								}
								resDto.setCreaditPayable(creaditPayable);
								resDto.setSelfPayable(selfPayable);
							}
							
						}else if(resDto.getAmbulanceChargesServiceId()!=null && resDto.getAmbulanceChargesServiceId().equals(serviceId)) 
						{  
							Double serviceAmount1 =  consumptionDto.getChangedPrice() != null ? consumptionDto.getChangedPrice() : consumptionDto.getFinalPrice() ;
							
							if(concessionPer>0.00)
								concession = (serviceAmount1*qty)*(concessionPer*0.01);
							else
								concession = 0.00;
							
							if(consumptionDto.getOrdDiscountPer()>0)
							{
								rateMinusConcession = (serviceAmount1*qty) - concession;
								discountAmt = rateMinusConcession*(consumptionDto.getOrdDiscountPer()*0.01);
							}
							if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
							{
								List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
								for (ContractConsumptionDto gstList : gstDetailsList) {
									if(gstList.getIsGstApplicable()==true && (consumptionDto.getFinalPrice()*qty)>=gstList.getGstAmountRange())
									{
										resDto.setTaxId(0);
										resDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
										taxAmt = ((consumptionDto.getFinalPrice()*qty)-Math.abs(concession+discountAmt))*(gstList.getGstPercentage()*0.01);
									}
									else
									{
										resDto.setTaxId(0);
										resDto.setTaxPercentage(0.00);
										taxAmt =  0.00;
									}
								}
							}
							else
							{
							if(resDto.getTaxPercentage()!=null)
							{
								taxAmt = ((serviceAmount1*qty)-Math.abs(concession+discountAmt))*(resDto.getTaxPercentage()*0.01);
							}else{
								taxAmt =  0.00;
							}
							}
							resDto.setTaxAmt(taxAmt);
							resDto.setFinalPrice(((serviceAmount1*qty)-(Math.abs(concession+discountAmt))) + taxAmt);
							resDto.setFinalPrice(new Double(OrderMasterServiceImpl.df2.format(serviceAmount1)));
							resDto.setServiceRate(serviceAmount1);
							resDto.setSelfPayable(serviceAmount1*((resDto.getCoShare()!=null ? resDto.getCoShare() : 0.00 )*0.01));
							resDto.setSelfPayable(new Double(OrderMasterServiceImpl.df2.format(resDto.getSelfPayable())));
							resDto.setCreaditPayable(serviceAmount1-resDto.getSelfPayable());
							
							
						}else if(consumptionDto.getChangedPrice()!=null && consumptionDto.getChangedPrice()>=0) 
						{
							if(concessionPer>0.00)
								concession = (consumptionDto.getChangedPrice()*qty)*(concessionPer*0.01);
							else
								concession = 0.00;
							
							if(consumptionDto.getOrdDiscountPer()>0)
							{
								rateMinusConcession = (consumptionDto.getChangedPrice()*qty) - concession;
								discountAmt = rateMinusConcession*(consumptionDto.getOrdDiscountPer()*0.01);
							}
							if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
							{
								List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
								for (ContractConsumptionDto gstList : gstDetailsList) {
									if(gstList.getIsGstApplicable()==true && (consumptionDto.getChangedPrice()*qty)>=gstList.getGstAmountRange())
									{
										resDto.setTaxId(0);
										resDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
										taxAmt = ((consumptionDto.getChangedPrice()*qty)-Math.abs(concession+discountAmt))*(gstList.getGstPercentage()*0.01);
									}
									else
									{
										resDto.setTaxId(0);
										resDto.setTaxPercentage(0.00);
										taxAmt =  0.00;
									}
								}
							}
							else
							{
							if(resDto.getTaxPercentage()!=null)
							{
								taxAmt = ((consumptionDto.getChangedPrice()*qty)-Math.abs(concession+discountAmt))*(resDto.getTaxPercentage()*0.01);
							}else{
								taxAmt =  0.00;
							}
							}
							resDto.setTaxAmt(taxAmt);
							resDto.setFinalPrice(((consumptionDto.getChangedPrice()*qty)-(Math.abs(concession+discountAmt))) + taxAmt);
							resDto.setFinalPrice(new Double(OrderMasterServiceImpl.df2.format(resDto.getFinalPrice())));
							resDto.setServiceRate(consumptionDto.getChangedPrice());
							resDto.setSelfPayable(resDto.getFinalPrice()*((resDto.getCoShare()!=null ? resDto.getCoShare() : 0.00 )*0.01));
							resDto.setSelfPayable(new Double(OrderMasterServiceImpl.df2.format(resDto.getSelfPayable())));
							resDto.setCreaditPayable(resDto.getFinalPrice()-resDto.getSelfPayable());
						}else if(consumptionDto.getProcedureId()!=null && consumptionDto.getProcedureId()>0)
						{
							rate = consumptionDto.getServiceRate()*qty;
							if(resDto.getIsTariffRateApplicable()=='Y')
							{
								resDto.setServiceRate(rate);
								if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
								{
									List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
									for (ContractConsumptionDto gstList : gstDetailsList) {
										if(gstList.getIsGstApplicable()==true && rate>=gstList.getGstAmountRange())
										{
											resDto.setTaxId(0);
											resDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
											taxAmt = rate * (gstList.getGstPercentage()*0.01);
										}
										else
										{
											resDto.setTaxId(0);
											resDto.setTaxPercentage(0.00);
											taxAmt =  0.00;
										}
									}
								}
								else
								{
								if(resDto.getTaxPercentage()!=null)
								{
									taxAmt = rate * (resDto.getTaxPercentage()*0.01);
									rate = rate+taxAmt;
								}
								}
								resDto.setFinalPrice(rate);
								resDto.setFinalPrice(new Double(OrderMasterServiceImpl.df2.format(resDto.getFinalPrice())));
								resDto.setTaxAmt(taxAmt);
								resDto.setSelfPayable(resDto.getFinalPrice()*((resDto.getCoShare()!=null ? resDto.getCoShare() : 0.00 )*0.01));
								resDto.setSelfPayable(new Double(OrderMasterServiceImpl.df2.format(resDto.getSelfPayable())));
								resDto.setCreaditPayable(resDto.getFinalPrice()-resDto.getSelfPayable());
							}
							resDto.setBillingBedExist(1);
						}else if(consumptionDto.getIsPeriodicityService()!=null && consumptionDto.getIsPeriodicityService()=='Y')
						{
							rate = consumptionDto.getServiceRate()*qty;
							if(resDto.getIsTariffRateApplicable()=='Y')
							{
								resDto.setServiceRate(rate);
								if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
								{
									List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
									for (ContractConsumptionDto gstList : gstDetailsList) {
										if(gstList.getIsGstApplicable()==true && rate>=gstList.getGstAmountRange())
										{
											resDto.setTaxId(0);
											resDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
											taxAmt = rate * (gstList.getGstPercentage()*0.01);
										}
										else
										{
											resDto.setTaxId(0);
											resDto.setTaxPercentage(0.00);
											taxAmt =  0.00;
										}
									}
								}
								else
								{
								if(resDto.getTaxPercentage()!=null)
								{
									taxAmt = rate * (resDto.getTaxPercentage()*0.01);
									rate = rate+taxAmt;
								}
								}
								resDto.setFinalPrice(rate);
								resDto.setFinalPrice(new Double(OrderMasterServiceImpl.df2.format(resDto.getFinalPrice())));
								resDto.setTaxAmt(taxAmt);
								resDto.setSelfPayable(resDto.getFinalPrice()*((resDto.getCoShare()!=null ? resDto.getCoShare() : 0.00 )*0.01));
								resDto.setSelfPayable(new Double(OrderMasterServiceImpl.df2.format(resDto.getSelfPayable())));
								resDto.setCreaditPayable(resDto.getFinalPrice()-resDto.getSelfPayable());
							}
						}else{
							if(resDto.getType()==1)
							{
								if(concessionPer>0.00)
									concession = resDto.getServiceRate()*(concessionPer*0.01);
								
								if(consumptionDto.getOrdDiscountPer()>0)
								{
									rateMinusConcession = (resDto.getServiceRate()*qty) - concession;
									discountAmt = rateMinusConcession*(consumptionDto.getOrdDiscountPer()*0.01);
								}
								if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
								{
									List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
									for (ContractConsumptionDto gstList : gstDetailsList) {
										if(gstList.getIsGstApplicable()==true && (resDto.getServiceRate()*qty)>=gstList.getGstAmountRange())
										{
											resDto.setTaxId(0);
											resDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
											taxAmt = ((resDto.getServiceRate()*qty)-Math.abs(concession+discountAmt))*(gstList.getGstPercentage()*0.01);
										}
										else
										{
											resDto.setTaxId(0);
											resDto.setTaxPercentage(0.00);
											taxAmt =  0.00;
										}
									}
								}
								else
								{
								if(resDto.getTaxPercentage()!=null)
								{
									taxAmt = ((resDto.getServiceRate()*qty)-Math.abs(concession+discountAmt))*(resDto.getTaxPercentage()*0.01);
								}else{
									taxAmt =  0.00;
								}
								}
								resDto.setTaxAmt(taxAmt);
								resDto.setFinalPrice((resDto.getServiceRate()*qty)-Math.abs(concession+discountAmt) + taxAmt);
								resDto.setFinalPrice(new Double(OrderMasterServiceImpl.df2.format(resDto.getFinalPrice())));
								resDto.setSelfPayable(resDto.getFinalPrice()*(resDto.getCoShare()*0.01));
								resDto.setSelfPayable(new Double(OrderMasterServiceImpl.df2.format(resDto.getSelfPayable())));
								resDto.setCreaditPayable(resDto.getFinalPrice()-resDto.getSelfPayable());
							}
							
						}
					}
					//}
					
					if(consumptionDto.getChangedPrice()==null && consumptionDto.getServiceChargeStatus()!=null && consumptionDto.getServiceChargeStatus()==0)
					{
						resDto.setServiceRate(resDto.getServiceRate()/2);
						resDto.setFinalPrice(resDto.getFinalPrice()/2);
						resDto.setCreaditPayable(resDto.getCreaditPayable()/2);
						resDto.setSelfPayable(resDto.getSelfPayable()/2);
					}
					
					//<-----1.2 First check will be from the highest priority's sponsors/payer 
					if(totalCreditPayable==0)
					{
						priorityId = resDto.getPriorityId();
						isRateEditable = resDto.getIsRateEditable();
						isRateEditableService = resDto.getIsRateEditable();
						isQuantityEditable = resDto.getIsQuantityEditable();
						preAuthAmount = resDto.getPreAuthBalAmt();
						if(resDto!=null)
						{
							//Type decides whether data has came from service contract or service cap
							//<-----1.2.1 if data has came from service contract
							if(resDto.getType()==1)
							{
								rate = (resDto.getFinalPrice()!=null ? resDto.getFinalPrice():resDto.getServiceRate());
								//<-----1.2.2 If cap amount is un-defined, then the service amount included in contract will be credit payable.
								if(resDto.getBalanceCapAmount()==null)
								{
								    selfPayable = resDto.getSelfPayable();
								    creaditPayable = resDto.getCreaditPayable();
								    
									if(preAuthAmount!=null && consumptionDto.isApplyAuth())
									{
										if(creaditPayable>preAuthAmount)
										{
											selfPayable = selfPayable + creaditPayable - preAuthAmount;
											creaditPayable = preAuthAmount;
										}
										if(consumptionDto.getUpdateCap()==null && creaditPayable>0)
											iContractConsumptionDao.updatePreAuthAmount(resDto.getPreauthId(),creaditPayable);
									}
									
									
									totalCreditPayable = totalCreditPayable+creaditPayable;
									totalSelfPayable = totalSelfPayable+selfPayable;
								}
								//1.2.2 ----->
								
								//<-----1.2.3 If cap amount is defined
								if(resDto.getBalanceCapAmount()!=null)
								{
									//<---1.2.3.1 If cap amount is greater than credit payable amount then assign the self and credit values as it is
									if(resDto.getCreaditPayable() <= resDto.getBalanceCapAmount())
									{
										selfPayable = resDto.getSelfPayable();
										creaditPayable =  resDto.getCreaditPayable();
									}
									//1.2.3.1--->
									
									//<---1.2.3.2 Else whatever amount remaining in the cap will be credit payable and rest of the amount will be self payable
									else
									{
										selfPayable = resDto.getSelfPayable()+(resDto.getCreaditPayable()-resDto.getBalanceCapAmount());
										creaditPayable = resDto.getBalanceCapAmount();
									}
									//1.2.3.2 --->

									if(preAuthAmount!=null && consumptionDto.isApplyAuth())
									{
										if(creaditPayable>preAuthAmount)
										{
											selfPayable = selfPayable + creaditPayable - preAuthAmount;
											creaditPayable = preAuthAmount;
										}
										if(consumptionDto.getUpdateCap()==null && creaditPayable>0)
											iContractConsumptionDao.updatePreAuthAmount(resDto.getPreauthId(),creaditPayable);
									}
									
									//<---1.2.3.3 update cap amount
									if(consumptionDto.getUpdateCap()==null && creaditPayable>0)
									{
											
											consumptionDto.setSpecialityId(resDto.getSpecialityId());
											consumptionDto.setContractId(resDto.getContractId());
										iContractConsumptionDao.updateCapAmount(consumptionDto,creaditPayable); 
									}
									//1.2.3.3--->
									
									totalCreditPayable = totalCreditPayable+creaditPayable;
									totalSelfPayable = totalSelfPayable+selfPayable;
								}
								//1.2.3----->
								
							}
							//1.2.1----->
							
							//<-----1.2.2 if data has came from service cap
							if(resDto.getType()==2)
							{
								
								Double netAmountSingleQty = 0.00;
								
								if(consumptionDto.getQuantity()!=null && consumptionDto.getQuantity()>1)
								{
									qty = consumptionDto.getQuantity();
								}
								
								//<-----1.2.2.1 check the applicable days lies between the date of admission and current date's difference
								if(consumptionDto.getAdmissionId()!=null)
									resDto.setDayDiff(GlobalCommonDateUtils.getDateDifference(GlobalCommonDateUtils.getDate(consumptionDto.getCurrentDate().substring(0,10),"yyyy-MM-dd"), GlobalCommonDateUtils.getDate(resDto.getDoa().substring(0,10),"yyyy-MM-dd"))+1);
								
								if(resDto.getTillDischarge()==true||resDto.getApplicableDays()>=resDto.getDayDiff())
								{
									resDto.setType(3);
									if(resDto.getApplicableDate()!=null && resDto.getApplicableDate().length()>0 && !(resDto.getApplicableDate().equals(consumptionDto.getCurrentDate().substring(0,10))))
									{
											resDto.setType(4);
											resDto.setBalanceAmount(resDto.getAmount());
											resDto.setBalanceQuantity(resDto.getQuantity());
											resDto.setApplicableDate(consumptionDto.getCurrentDate().substring(0,10));
									}
									
									//<-----1.2.2.1.1 if yes then get the rates from the fall back tariff.
									if(consumptionDto.getChangedPrice()!=null && consumptionDto.getChangedPrice()>=0)
									{
										rate = consumptionDto.getChangedPrice()*qty;
										concession = rate*(concessionPer*0.01);	
										if(consumptionDto.getOrdDiscountPer()>0)
										{
											rateMinusConcession = rate - concession;
											discountAmt = rateMinusConcession*(consumptionDto.getOrdDiscountPer()*0.01);
										}
									}else if(consumptionDto.getConsultationAmount()!=null && consumptionDto.getConsultationAmount()>0)
									{
										rate = consumptionDto.getConsultationAmount()*qty;
										concession = rate*(concessionPer*0.01);	
									}else if(consumptionDto.getIsPeriodicityService()!=null && consumptionDto.getIsPeriodicityService()=='Y')
									{
										rate = consumptionDto.getServiceRate()*qty;
										concession = rate*(concessionPer*0.01);	
									}else if(consumptionDto.getProcedureId()!=null && consumptionDto.getProcedureId()>0)
									{
										rate = consumptionDto.getServiceRate()*qty;
										concession = rate*(concessionPer*0.01);	
									}else{
										UnitServiceTariffMasterDto unitServiceTariffMasterDto =new UnitServiceTariffMasterDto();
											unitServiceTariffMasterDto.setOrganizationId(consumptionDto.getOrganizationId());
											unitServiceTariffMasterDto.setUnitId(consumptionDto.getUnitId());
											unitServiceTariffMasterDto.setVisitTypeId(consumptionDto.getVisitTypeId());
											unitServiceTariffMasterDto.setBillingBedCategoryId(consumptionDto.getBillingBedCategoryId());
											unitServiceTariffMasterDto.setPatientTypeId(consumptionDto.getPatientCategoryId()!=null ? consumptionDto.getPatientCategoryId() : consumptionDto.getPatientTypeId()); 
											unitServiceTariffMasterDto.setPaymentEntitlementId(1);//Self
											unitServiceTariffMasterDto.setServiceId(consumptionDto.getServiceId());
											unitServiceTariffMasterDto.setOrderDate(consumptionDto.getCurrentDate());
											unitServiceTariffMasterDto.setTariffId(resDto.getTariffId());
											unitServiceTariffMasterDto.setPatientClassId(consumptionDto.getPatientClassId());
											unitServiceTariffMasterDto = iEncounterMasterService.getBasePriceByServiceTariffMaster(unitServiceTariffMasterDto);
										rate = (unitServiceTariffMasterDto.getRate() != null ? unitServiceTariffMasterDto.getRate() : 0.0);
										
										concession = rate*(concessionPer*0.01);	
									}
									if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
									{
										List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
										for (ContractConsumptionDto gstList : gstDetailsList) {
											if(gstList.getIsGstApplicable()==true && rate>=gstList.getGstAmountRange())
											{
												resDto.setTaxId(0);
												resDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
												taxAmt = (rate-(concession+discountAmt)) * (gstList.getGstPercentage()*0.01);
												netAmt = (rate-(concession+discountAmt))+taxAmt;
											}
											else
											{
												resDto.setTaxId(0);
												resDto.setTaxPercentage(0.00);
												netAmt = (rate-(concession+discountAmt));
											}
										}
									}
									else
									{
									if(resDto.getTaxPercentage()!=null && resDto.getTaxPercentage()>0)
									{
										taxAmt = (rate-(concession+discountAmt)) * (resDto.getTaxPercentage()*0.01);
										netAmt = (rate-(concession+discountAmt))+taxAmt;
									}
									else
										netAmt = (rate-(concession+discountAmt));
									}
									
									if(consumptionDto.getChangedPrice()==null && consumptionDto.getServiceChargeStatus()!=null && consumptionDto.getServiceChargeStatus()==0)
									{
										rate = rate/2;
									}
									
									if(consumptionDto.getQuantity()!=null && consumptionDto.getQuantity()>1)
									{
										netAmountSingleQty = netAmt / consumptionDto.getQuantity();
									}
									
									//1.2.2.1.1----->
									
									//<-----1.2.2.1.2 If service capping is based on amount
									if(resDto.getBalanceAmount()!=null && resDto.getBalanceAmount()>0)
									{
										//<---1.2.2.1.2.1 If cap amount is greater than service rates then assign the rates to credit payable
										if(netAmt < resDto.getBalanceAmount())
										{
											creaditPayable = netAmt;
											selfPayable = 0.0;
										}
										//1.2.2.1.2.1--->
										
										//<---1.2.2.1.2.2 Else whatever amount remaining in the cap will be credit payable rest o the amount will be self payable
										else
										{
											selfPayable=netAmt-resDto.getBalanceAmount();
											creaditPayable=resDto.getBalanceAmount();
										}
										//1.2.2.1.2.2--->
										
										if(resDto.getCoShare()!=null && creaditPayable > 0)
										{
											selfPayable = selfPayable + (creaditPayable*resDto.getCoShare()*0.01);
											creaditPayable = creaditPayable - (creaditPayable*resDto.getCoShare()*0.01);
										}
										
										if(preAuthAmount!=null && consumptionDto.isApplyAuth())
										{
											if(creaditPayable>preAuthAmount)
											{
												selfPayable = selfPayable + creaditPayable - preAuthAmount;
												creaditPayable = preAuthAmount;
											}
											if(consumptionDto.getUpdateCap()==null && creaditPayable>0)
												iContractConsumptionDao.updatePreAuthAmount(resDto.getPreauthId(),creaditPayable);
										}
										
										
										//<---1.2.2.1.2.3 update cap amount
										if(consumptionDto.getUpdateCap()==null && creaditPayable>0)
										{
											iContractConsumptionDao.updateServiceCapAmount(resDto,creaditPayable.doubleValue(),null); 
										}
											
										//1.2.2.1.2.3--->
										totalCreditPayable = totalCreditPayable+creaditPayable;
										totalSelfPayable = totalSelfPayable+selfPayable;
									}
									//1.2.2.1.2----->
									
									//<-----1.2.2.1.3 If service capping is based on quantity
									if(resDto.getBalanceQuantity()!=null)
									{
										//<---1.2.2.1.3.1 If quantity exists then service rates will be assigned to credit payable
										if(resDto.getBalanceQuantity()>0)
										{
											
											Double qtyToBeUpdated = 1.0;
											
											if(consumptionDto.getQuantity()!=null && consumptionDto.getQuantity()>1)
											{
												Double diffQty = 0.00;
												if(resDto.getBalanceQuantity()>=consumptionDto.getQuantity())
												{
													creaditPayable = netAmt;
													selfPayable = 0.0;
													
													qtyToBeUpdated = consumptionDto.getQuantity();
												}else{
													qtyToBeUpdated = resDto.getBalanceQuantity();
													
													diffQty = consumptionDto.getQuantity() - resDto.getBalanceQuantity();
													creaditPayable = resDto.getBalanceQuantity() * netAmountSingleQty;
													selfPayable = diffQty * netAmountSingleQty;
												}
												
											}else{
												creaditPayable = netAmt;
												selfPayable = 0.0;
											}
											
											if(resDto.getCoShare()!=null && creaditPayable > 0)
											{
												selfPayable = selfPayable + (creaditPayable*resDto.getCoShare()*0.01);
												creaditPayable = creaditPayable - (creaditPayable*resDto.getCoShare()*0.01);
											}
											
											if(preAuthAmount!=null && consumptionDto.isApplyAuth())
											{
												if(creaditPayable>preAuthAmount)
												{
													selfPayable = selfPayable + creaditPayable - preAuthAmount;
													creaditPayable = preAuthAmount;
												}
												if(consumptionDto.getUpdateCap()==null && creaditPayable>0)
													iContractConsumptionDao.updatePreAuthAmount(resDto.getPreauthId(),creaditPayable);
											}
											
											//<---1.2.2.1.3.1.1 update quantity
											if(consumptionDto.getUpdateCap()==null && creaditPayable > 0)
											{
												iContractConsumptionDao.updateServiceCapAmount(resDto,null,(qtyToBeUpdated!=null && qtyToBeUpdated>1) ? qtyToBeUpdated : 1.00); 
											}
											//1.2.2.1.3.1.1--->
											
											totalCreditPayable = totalCreditPayable+creaditPayable;
											totalSelfPayable = totalSelfPayable+selfPayable;
										}
										//1.2.2.1.3.1--->
										
										//<---1.2.2.1.3.2 If quantity exceeds, then service rates will be assigned to self payable
										else
										{
											creaditPayable = 0.0;
											selfPayable =netAmt;
											
											totalCreditPayable = totalCreditPayable+creaditPayable;
											totalSelfPayable = totalSelfPayable+selfPayable;
										}
										//1.2.2.1.3.2--->
									}
									//1.2.2.1.3----->
								}
								//1.2.2.1----->
							}
							//1.2.2----->
						}
						if(creaditPayable>0)
						{
							detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
								detailsPayeeMapperDto.setPayeeId(resDto.getPayeeId());
								detailsPayeeMapperDto.setAssociateCompanyId(resDto.getAssociateCompanyId());
								detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(creaditPayable));
								detailsPayeeMapperDto.setContractId(resDto.getContractId());
								detailsPayeeMapperDto.setServiceId(serviceId);
								detailsPayeeMapperDto.setPayeeDesc(resDto.getCompanyName());
								detailsPayeeMapperDto.setIsRateEditable(isRateEditable);
								detailsPayeeMapperDto.setCoShare(resDto.getCoShare());
								detailsPayeeMapperDto.setSpiltBillAmt(new BigDecimal(creaditPayable));
							orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
						}
					}
					//1.2----->
					
					//<-----1.3 Second round, after consuming the service from the highest priority's payer, if there is a still self payable component exist then check with the next sponsor/payer
					if(totalSelfPayable>0)
					{
						List<ContractConsumptionDto> serviceDetailslistsecondRoundList= serviceDetailslist;
						for(ContractConsumptionDto secondRound : serviceDetailslistsecondRoundList)
						{
							Double creaditPayableSecondRound = 0.00;
							Double secondRounfCoShare = 0.00;
							if(secondRound.getPriorityId() > priorityId)
							{
								
								if(consumptionDto.getQuantity()!=null && consumptionDto.getQuantity()>1)
								{
									qty = consumptionDto.getQuantity();
									secondRound.setSelfPayable(consumptionDto.getQuantity()*secondRound.getSelfPayable());
									secondRound.setCreaditPayable(consumptionDto.getQuantity()*secondRound.getCreaditPayable());
									secondRound.setFinalPrice(consumptionDto.getQuantity()*secondRound.getFinalPrice());
								}
								
								preAuthAmount = secondRound.getPreAuthBalAmt();
								secondRound.setEncounterId(consumptionDto.getEncounterId());
								secondRound.setAdmissionId(consumptionDto.getAdmissionId());
								
								if(secondRound.getType()==1)	
								{	
									consumptionDto.setSpecialityId(secondRound.getSpecialityId());
									if(secondRound.getBalanceCapAmount()==null)
									{
										if(secondRound.getCreaditPayable() > totalSelfPayable)
											creaditPayableSecondRound = totalSelfPayable;
										else
											creaditPayableSecondRound = secondRound.getCreaditPayable();
										
									}
									if(secondRound.getBalanceCapAmount()!=null)
									{
										if(secondRound.getCreaditPayable() >= totalSelfPayable)
										{
											if(totalSelfPayable < secondRound.getBalanceCapAmount())
												creaditPayableSecondRound=totalSelfPayable;
											else
												creaditPayableSecondRound=secondRound.getBalanceCapAmount();
										}
										else if(secondRound.getCreaditPayable() < totalSelfPayable)
										{
											if(secondRound.getCreaditPayable() < secondRound.getBalanceCapAmount())
												creaditPayableSecondRound=secondRound.getCreaditPayable();
											else
												creaditPayableSecondRound=secondRound.getBalanceCapAmount();
										}
									}
									
									totalCreditPayable = totalCreditPayable+creaditPayableSecondRound;
									totalSelfPayable = totalSelfPayable-creaditPayableSecondRound;
									
									if(creaditPayableSecondRound>0)
									{
										if(secondRound.getCoShare()!=null && secondRound.getCoShare()>0)
										{
											secondRounfCoShare  = ((secondRound.getCoShare()*0.01)*creaditPayableSecondRound);
											creaditPayableSecondRound = creaditPayableSecondRound - secondRounfCoShare ;
											totalSelfPayable = totalSelfPayable+secondRounfCoShare;
											totalCreditPayable =  totalCreditPayable-secondRounfCoShare;
										}
										
										if(preAuthAmount!=null && consumptionDto.isApplyAuth())
										{
											if(creaditPayableSecondRound>preAuthAmount)
											{
												double diff = creaditPayableSecondRound - preAuthAmount;
												creaditPayableSecondRound = preAuthAmount;
												
												totalSelfPayable = totalSelfPayable+diff;
												totalCreditPayable =  totalCreditPayable-diff;
											}
											if(consumptionDto.getUpdateCap()==null && creaditPayableSecondRound>0)
												iContractConsumptionDao.updatePreAuthAmount(secondRound.getPreauthId(),creaditPayableSecondRound);	
										}
										
										consumptionDto.setContractId(secondRound.getContractId());
										if(consumptionDto.getUpdateCap()==null && secondRound.getBalanceCapAmount()!=null && creaditPayableSecondRound>0)
											iContractConsumptionDao.updateCapAmount(consumptionDto,creaditPayableSecondRound);
									}
									
								}
								
								if(secondRound.getType()==2)
								{
									
									if(consumptionDto.getAdmissionId()!=null)
										resDto.setDayDiff(GlobalCommonDateUtils.getDateDifference(GlobalCommonDateUtils.getDate(consumptionDto.getCurrentDate().substring(0,10),"yyyy-MM-dd"), GlobalCommonDateUtils.getDate(secondRound.getDoa().substring(0,10),"yyyy-MM-dd"))+1);
									
									if(secondRound.getTillDischarge()==true||secondRound.getApplicableDays()>=secondRound.getDayDiff())
									{
										secondRound.setType(3);
										if(secondRound.getApplicableDate()!=null && !(secondRound.getApplicableDate().equals(consumptionDto.getCurrentDate().substring(0,10))))
										{
											secondRound.setType(4);
											secondRound.setBalanceAmount(secondRound.getAmount());
											secondRound.setBalanceQuantity(secondRound.getQuantity());
											secondRound.setApplicableDate(consumptionDto.getCurrentDate().substring(0,10));
										}
										if((secondRound.getBalanceAmount()!=null && secondRound.getBalanceAmount()>0)||secondRound.getBalanceQuantity()!=null)
										{
										
											Double balCap = secondRound.getBalanceAmount();
											if(balCap!=null && balCap>0.00)
											{
												if(totalSelfPayable <= balCap)
												{
													creaditPayableSecondRound = totalSelfPayable;
												}else{
													creaditPayableSecondRound=balCap;
												}
												
												totalCreditPayable = totalCreditPayable+creaditPayableSecondRound;
												totalSelfPayable = totalSelfPayable-creaditPayableSecondRound;
												
												
												if(secondRound.getCoShare()!=null && secondRound.getCoShare()>0)
												{
													secondRounfCoShare  = ((secondRound.getCoShare()*0.01)*creaditPayableSecondRound);
													creaditPayableSecondRound = creaditPayableSecondRound - secondRounfCoShare ;
													totalSelfPayable = totalSelfPayable+secondRounfCoShare;
													totalCreditPayable =  totalCreditPayable-secondRounfCoShare;
												}
												
												if(preAuthAmount!=null && consumptionDto.isApplyAuth())
												{
													if(creaditPayableSecondRound>preAuthAmount)
													{
														double diff = creaditPayableSecondRound - preAuthAmount;
														creaditPayableSecondRound = preAuthAmount;
														
														totalSelfPayable = totalSelfPayable+diff;
														totalCreditPayable =  totalCreditPayable-diff;
													}
													
													if(consumptionDto.getUpdateCap()==null && creaditPayableSecondRound>0)
														iContractConsumptionDao.updatePreAuthAmount(secondRound.getPreauthId(),creaditPayableSecondRound);
												}
												
												if(consumptionDto.getUpdateCap()==null && creaditPayableSecondRound.doubleValue()>0.00)
												{
													iContractConsumptionDao.updateServiceCapAmount(secondRound,creaditPayableSecondRound.doubleValue(),null); 
												}
												
											}
											if(secondRound.getBalanceQuantity()!=null)
											{
													
												UnitServiceTariffMasterDto unitServiceTariffMasterDto =new UnitServiceTariffMasterDto();
													unitServiceTariffMasterDto.setOrganizationId(consumptionDto.getOrganizationId());
													unitServiceTariffMasterDto.setUnitId(consumptionDto.getUnitId());
													unitServiceTariffMasterDto.setVisitTypeId(consumptionDto.getVisitTypeId());
													unitServiceTariffMasterDto.setBillingBedCategoryId(consumptionDto.getBillingBedCategoryId());
													unitServiceTariffMasterDto.setPatientTypeId(consumptionDto.getPatientCategoryId()!=null ? consumptionDto.getPatientCategoryId() : consumptionDto.getPatientTypeId()); 
													unitServiceTariffMasterDto.setPaymentEntitlementId(1);//Self
													unitServiceTariffMasterDto.setServiceId(consumptionDto.getServiceId());
													unitServiceTariffMasterDto.setOrderDate(consumptionDto.getCurrentDate());
													unitServiceTariffMasterDto.setTariffId(secondRound.getTariffId());
													unitServiceTariffMasterDto.setPatientClassId(consumptionDto.getPatientClassId());
													unitServiceTariffMasterDto = iEncounterMasterService.getBasePriceByServiceTariffMaster(unitServiceTariffMasterDto);
												rate = (unitServiceTariffMasterDto.getRate() != null ? unitServiceTariffMasterDto.getRate() : 0.0);
												if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
												{
													List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
													for (ContractConsumptionDto gstList : gstDetailsList) {
														if(gstList.getIsGstApplicable()==true && rate>=gstList.getGstAmountRange())
														{
															resDto.setTaxId(0);
															resDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
															taxAmt = rate * (gstList.getGstPercentage()*0.01);
															netAmt = rate+taxAmt;
														}
														else
														{
															resDto.setTaxId(0);
															resDto.setTaxPercentage(0.00);
															netAmt = rate;
														}
													}
												}
												else
												{
												if(secondRound.getTaxPercentage()!=null)
												{
													taxAmt = rate * (resDto.getTaxPercentage()*0.01);
													netAmt = rate+taxAmt;
												}
												else
													netAmt = rate;
												}
												
												if(consumptionDto.getServiceChargeStatus()!=null && consumptionDto.getServiceChargeStatus()==0)
												{
													netAmt = netAmt/2;
												}
												
												if(secondRound.getBalanceQuantity()!=null && secondRound.getBalanceQuantity()>0)
												{
													orderDetailsPayeeMapperDtosList=new LinkedList<>();
													
													creaditPayableSecondRound = netAmt;
													
													totalCreditPayable = netAmt;
													totalSelfPayable = 0.0;
													
													if(secondRound.getCoShare()!=null && secondRound.getCoShare()>0)
													{
														secondRounfCoShare  = ((secondRound.getCoShare()*0.01)*creaditPayableSecondRound);
														creaditPayableSecondRound = creaditPayableSecondRound - secondRounfCoShare ;
														totalSelfPayable = totalSelfPayable+secondRounfCoShare;
														totalCreditPayable =  totalCreditPayable-secondRounfCoShare;
													}
													
													if(preAuthAmount!=null && consumptionDto.isApplyAuth())
													{
														if(creaditPayableSecondRound>preAuthAmount)
														{
															double diff = creaditPayableSecondRound - preAuthAmount;
															creaditPayableSecondRound = preAuthAmount;
															
															totalSelfPayable = totalSelfPayable+diff;
															totalCreditPayable =  totalCreditPayable-diff;
														}
														if(consumptionDto.getUpdateCap()==null && creaditPayableSecondRound>0)
															iContractConsumptionDao.updatePreAuthAmount(secondRound.getPreauthId(),creaditPayableSecondRound);
													}
													
													if(consumptionDto.getUpdateCap()==null && creaditPayableSecondRound > 0)
													{
														iContractConsumptionDao.updateServiceCapAmount(secondRound,null,1.00); 
													}
														
												}
											}
										}
									}
								}
							
								if(creaditPayableSecondRound>0)
								{
									detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
										detailsPayeeMapperDto.setPayeeId(secondRound.getPayeeId());
										detailsPayeeMapperDto.setAssociateCompanyId(secondRound.getAssociateCompanyId());
										detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(creaditPayableSecondRound));
										detailsPayeeMapperDto.setContractId(secondRound.getContractId());
										detailsPayeeMapperDto.setPayeeDesc(secondRound.getCompanyName());
										detailsPayeeMapperDto.setServiceId(serviceId);
										detailsPayeeMapperDto.setIsRateEditable('N');
										detailsPayeeMapperDto.setCoShare(secondRound.getCoShare());
										detailsPayeeMapperDto.setSpiltBillAmt(new BigDecimal(creaditPayableSecondRound));
									orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
								}
							
							}	
						count++;
						}
							
					}
					//1.3----->
					
					if((totalSelfPayable==0 && totalCreditPayable>0) || serviceDetailslist.size()==count)
					{	
						if(resDto.getBillingBedExist()!=null)
						{
							finalDto.setTaxAmt(taxAmt);
							finalDto.setCoShare(resDto.getCoShare()!=null ? resDto.getCoShare() : 0.00);
							finalDto.setMinRateEditable(resDto.getMinRateEditable());
							finalDto.setMaxRateEditable(resDto.getMaxRateEditable());
							finalDto.setServiceRate(resDto.getServiceRate()!=null && resDto.getServiceRate()>0 ? resDto.getServiceRate() : rate/qty);
							finalDto.setFinalPrice(resDto.getFinalPrice()!=null && resDto.getFinalPrice()>0 ? resDto.getFinalPrice() : netAmt);
							finalDto.setNetAmount(resDto.getFinalPrice()!=null && resDto.getFinalPrice()>0 ? resDto.getFinalPrice() : netAmt);
							finalDto.setOrdTotalAmount(resDto.getServiceRate()!=null && resDto.getServiceRate()>0 ? resDto.getServiceRate() : rate);
							finalDto.setCreaditPayable(totalCreditPayable);
							finalDto.setSelfPayable(totalSelfPayable);
							finalDto.setConcession(concession);
							finalDto.setPayeeId(resDto.getPayeeId());
							finalDto.setSelfPayeeId(resDto.getSelfPayeeId());
							finalDto.setServiceId(serviceId);
								detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
									detailsPayeeMapperDto.setPayeeId(resDto.getSelfPayeeId());
									detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(totalSelfPayable));
									detailsPayeeMapperDto.setServiceId(resDto.getServiceId());
									detailsPayeeMapperDto.setIsRateEditable('N');
									detailsPayeeMapperDto.setCoShare(0.00);
								orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
							finalDto.setTaxId(resDto.getTaxId());
							finalDto.setTaxPercentage(resDto.getTaxPercentage());
							finalDto.setContractId(resDto.getContractId());
							finalDto.setPatientClassPercentage(concessionPer);
							finalDto.setMarkupStatus(resDto.getMarkupStatus());
							finalDto.setSpecialityId(specialityId);
							finalDto.setSubSpecialityId(subSpecialityId);
							finalDto.setIsRateEditable(isRateEditable!=null ? isRateEditable : 'N');
							//finalDto.setIsRateEditableService(isRateEditableService!=null ? isRateEditableService : 'N');
							finalDto.setIsQuantityEditable(isQuantityEditable!=null ? isQuantityEditable : 'N');
							finalDto.setTariffId(tariffId);
							finalDto.setOrderDetailsPayeeMapperDtosList(orderDetailsPayeeMapperDtosList);
							finalDto.setIsDocReq(resDto.getIsDocReq());
							finalDto.setIsRefReq(resDto.getIsRefReq());
							finalDto.setOrdDiscount(discountAmt);
							finalDto.setBillingBedCategoryId(consumptionDto.getBillingBedCategoryId());
							finalDto.setIsAutoRender(resDto.getIsAutoRender()!=null ? resDto.getIsAutoRender() : 'N');
							return new Response(SUCCESS,SUCCESS_CODE,null,serviceDetailslist,finalDto);
						}else
						{
							UnitServiceTariffMasterDto unitServiceTariffMasterDto =new UnitServiceTariffMasterDto();
								unitServiceTariffMasterDto.setOrganizationId(consumptionDto.getOrganizationId());
								unitServiceTariffMasterDto.setUnitId(consumptionDto.getUnitId());
								unitServiceTariffMasterDto.setVisitTypeId(consumptionDto.getVisitTypeId());
								unitServiceTariffMasterDto.setBillingBedCategoryId(consumptionDto.getBillingBedCategoryId());
								unitServiceTariffMasterDto.setPatientTypeId(consumptionDto.getPatientCategoryId()!=null ? consumptionDto.getPatientCategoryId() : consumptionDto.getPatientTypeId()); 
								unitServiceTariffMasterDto.setPaymentEntitlementId(1);//Self
								unitServiceTariffMasterDto.setServiceId(serviceId);
								unitServiceTariffMasterDto.setOrderDate(consumptionDto.getCurrentDate());
								unitServiceTariffMasterDto.setTariffId(resDto.getTariffId());
								unitServiceTariffMasterDto.setPatientClassId(consumptionDto.getPatientClassId());
							unitServiceTariffMasterDto = iEncounterMasterService.getBasePriceByServiceTariffMaster(unitServiceTariffMasterDto);
							
							if((unitServiceTariffMasterDto.getIsDiscount()!=null && unitServiceTariffMasterDto.getIsSchemeDiscount()!=null && ((unitServiceTariffMasterDto.getIsDiscount()=='Y' && unitServiceTariffMasterDto.getIsSchemeDiscount()=='Y')||(unitServiceTariffMasterDto.getIsSchemeDiscount()=='N'))))
							{
								if(consumptionDto.getPatientClassPercentage()!=null && consumptionDto.isPatientClassApplied()==false)
									concessionPer = consumptionDto.getPatientClassPercentage();
								else {
									patientClassDto = iContractConsumptionDao.getPatientClassAndPercentageByVisit(consumptionDto);	
									if(patientClassDto != null && patientClassDto.getPatientClassPercentage()!=null 
											 && patientClassDto.getMarkupStatus()==false 
											 && patientClassDto.getPatientClassPercentage()>0
											 && consumptionDto.getOrdDiscount()==0)
										concessionPer = patientClassDto.getPatientClassPercentage();
								}
							}else{
								concessionPer = 0.00;
							}
							
							
							if(unitServiceTariffMasterDto.getAmbulanceChargesServiceId()!= null && unitServiceTariffMasterDto.getAmbulanceChargesServiceId().equals(serviceId)){
								
								rate =  consumptionDto.getChangedPrice() != null ? consumptionDto.getChangedPrice() : consumptionDto.getFinalPrice() ;
								concession = (rate*concessionPer)*0.01;
								consumptionDto.setConcession(concession);
								if(consumptionDto.getOrdDiscountPer()>0)
								{
									rateMinusConcession = rate - concession;
									discountAmt = rateMinusConcession*(consumptionDto.getOrdDiscountPer()*0.01);
								}
								if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
								{
									List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
									for (ContractConsumptionDto gstList : gstDetailsList) {
										if(gstList.getIsGstApplicable()==true && consumptionDto.getChangedPrice()>=gstList.getGstAmountRange())
										{
											resDto.setTaxId(0);
											resDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
											netAmt = (consumptionDto.getChangedPrice()-Math.abs(concession+discountAmt)) + ((gstList.getGstPercentage()*0.01)*(consumptionDto.getChangedPrice()-Math.abs(concession+discountAmt)));
										}
										else
										{
											resDto.setTaxId(0);
											resDto.setTaxPercentage(0.00);
											netAmt = (consumptionDto.getChangedPrice()-Math.abs(concession+discountAmt)) + (0*(consumptionDto.getChangedPrice()-Math.abs(concession+discountAmt)));
										}
									}
								}
								else
								{	
								netAmt = (consumptionDto.getChangedPrice()-Math.abs(concession+discountAmt)) + ((unitServiceTariffMasterDto.getTaxPercentage()*0.01)*(consumptionDto.getChangedPrice()-Math.abs(concession+discountAmt)));
								}
							}else
							if(consumptionDto.getChangedPrice()!=null && consumptionDto.getChangedPrice()>=0)
							{
								rate = consumptionDto.getChangedPrice();
								concession = (rate*concessionPer)*0.01;
								consumptionDto.setConcession(concession);
								if(consumptionDto.getOrdDiscountPer()>0)
								{
									rateMinusConcession = rate - concession;
									discountAmt = rateMinusConcession*(consumptionDto.getOrdDiscountPer()*0.01);
								}
								if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
								{
									List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
									for (ContractConsumptionDto gstList : gstDetailsList) {
										if(gstList.getIsGstApplicable()==true && consumptionDto.getChangedPrice()>=gstList.getGstAmountRange())
										{
											resDto.setTaxId(0);
											resDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
											netAmt = (consumptionDto.getChangedPrice()-Math.abs(concession+discountAmt)) + ((gstList.getGstPercentage()*0.01)*(consumptionDto.getChangedPrice()-Math.abs(concession+discountAmt)));
										}
										else
										{
											resDto.setTaxId(0);
											resDto.setTaxPercentage(0.00);
											netAmt = (consumptionDto.getChangedPrice()-Math.abs(concession+discountAmt)) + (0*(consumptionDto.getChangedPrice()-Math.abs(concession+discountAmt)));
										}
									}
								}
								else
								{	
								netAmt = (consumptionDto.getChangedPrice()-Math.abs(concession+discountAmt)) + ((unitServiceTariffMasterDto.getTaxPercentage()*0.01)*(consumptionDto.getChangedPrice()-Math.abs(concession+discountAmt)));
								}
							}else {
								
								rate = (unitServiceTariffMasterDto.getRate() != null ? unitServiceTariffMasterDto.getRate() : 0.0);
								
								if(consumptionDto.getConsultationAmount()!=null)
									rate = consumptionDto.getConsultationAmount();
								else if(consumptionDto.getProcedureId()!=null && consumptionDto.getProcedureId()>0)
									rate = consumptionDto.getServiceRate();
								else if(consumptionDto.getIsPeriodicityService()!=null && consumptionDto.getIsPeriodicityService()=='Y')
									rate = consumptionDto.getServiceRate();
								else
									rate = (unitServiceTariffMasterDto.getRate() != null ? unitServiceTariffMasterDto.getRate() : 0.0);
								
								if(consumptionDto.getChangedPrice()==null && consumptionDto.getServiceChargeStatus()!=null && consumptionDto.getServiceChargeStatus()==0)
								{
									rate = rate/2;
									unitServiceTariffMasterDto.setFinalRate(unitServiceTariffMasterDto.getFinalRate()!=null && unitServiceTariffMasterDto.getFinalRate().intValue()>0 ?  unitServiceTariffMasterDto.getFinalRate()/2 : 0);
								}
							
								if(concessionPer>0)
								{
									concession = rate*(concessionPer*0.01);
								}
								if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
								{
									List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
									for (ContractConsumptionDto gstList : gstDetailsList) {
										if(gstList.getIsGstApplicable()==true && rate>=gstList.getGstAmountRange())
										{
											resDto.setTaxId(0);
											resDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
											netAmt = (rate-(concession+discountAmt)) + ((gstList.getGstPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
										}
										else
										{
											resDto.setTaxId(0);
											resDto.setTaxPercentage(0.00);
											netAmt = (rate-(concession+discountAmt)) + (0*(rate-Math.abs(concession+discountAmt)));
										}
									}
								}
								else
								{
								netAmt = (rate-(concession+discountAmt)) + ((unitServiceTariffMasterDto.getTaxPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
								}
							}
							
							if(netAmt > (resDto.getFinalPrice()!=null && resDto.getFinalPrice()>0 ? resDto.getFinalPrice() : netAmt))
							{
								Double difference = netAmt - (resDto.getFinalPrice()!=null && resDto.getFinalPrice()>0 ? resDto.getFinalPrice() : netAmt);
								
								if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
								{
									List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
									for (ContractConsumptionDto gstList : gstDetailsList) {
										if(gstList.getIsGstApplicable()==true && rate>=gstList.getGstAmountRange())
										{
											finalDto.setTaxId(0);
											finalDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
											finalDto.setTaxAmt((gstList.getGstPercentage()*0.01)*(rate-(concession+discountAmt)));
										}
										else
										{
											finalDto.setTaxId(0);
											finalDto.setTaxPercentage(0.00);
											finalDto.setTaxAmt(0*(rate-(concession+discountAmt)));
										}
									}
								}
								else
								{
									finalDto.setTaxAmt((unitServiceTariffMasterDto.getTaxPercentage()*0.01)*(rate-(concession+discountAmt)));
								}
									finalDto.setTaxId(resDto.getTaxId());
									finalDto.setTaxPercentage(resDto.getTaxPercentage());
									finalDto.setMinRateEditable(resDto.getMinRateEditable());
									finalDto.setMaxRateEditable(resDto.getMaxRateEditable());
									finalDto.setServiceRate(resDto.getServiceRate()!=null && resDto.getServiceRate()>0 ? resDto.getServiceRate() : rate);
									finalDto.setFinalPrice(resDto.getFinalPrice()!=null && resDto.getFinalPrice()>0 ? resDto.getFinalPrice() : netAmt);
									finalDto.setNetAmount(resDto.getFinalPrice()!=null && resDto.getFinalPrice()>0 ? resDto.getFinalPrice() : netAmt);
									finalDto.setOrdTotalAmount(resDto.getServiceRate()!=null && resDto.getServiceRate()>0 ? resDto.getServiceRate() : rate);
									finalDto.setCreaditPayable(totalCreditPayable);
									finalDto.setSelfPayable(totalSelfPayable+difference);
									finalDto.setConcession(concession);
									finalDto.setPayeeId(resDto.getPayeeId());
									finalDto.setSelfPayeeId(resDto.getSelfPayeeId());
									finalDto.setServiceId(serviceId);
										detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
											detailsPayeeMapperDto.setPayeeId(resDto.getSelfPayeeId());
											detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(totalSelfPayable+difference));
											detailsPayeeMapperDto.setServiceId(serviceId);
											detailsPayeeMapperDto.setIsRateEditable('N');
											detailsPayeeMapperDto.setCoShare(0.00);
										orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
									finalDto.setCoShare(resDto.getCoShare()!=null ? resDto.getCoShare() : 0.00);
									finalDto.setTaxPercentage(resDto.getTaxPercentage());
									finalDto.setContractId(resDto.getContractId());
									finalDto.setPatientClassPercentage(concessionPer);
									finalDto.setMarkupStatus(resDto.getMarkupStatus());
									finalDto.setSpecialityId(specialityId);
									finalDto.setSubSpecialityId(subSpecialityId);
									finalDto.setIsRateEditable(isRateEditable!=null ? isRateEditable : 'N');
									//finalDto.setIsRateEditableService(isRateEditableService!=null ? isRateEditableService : 'N');
									finalDto.setIsQuantityEditable(isQuantityEditable!=null ? isQuantityEditable : 'N');
									finalDto.setTariffId(tariffId);
									finalDto.setOrderDetailsPayeeMapperDtosList(orderDetailsPayeeMapperDtosList);
									finalDto.setIsDocReq(resDto.getIsDocReq());
									finalDto.setIsRefReq(resDto.getIsRefReq());
									finalDto.setBillingBedCategoryId(consumptionDto.getBillingBedCategoryId());
									finalDto.setIsAutoRender(resDto.getIsAutoRender()!=null ? resDto.getIsAutoRender() : 'N');
								return new Response(SUCCESS,SUCCESS_CODE,null,null,finalDto);
							}
							else
							{
									finalDto.setTaxAmt(taxAmt);
									finalDto.setTaxId(resDto.getTaxId());
									finalDto.setTaxPercentage(resDto.getTaxPercentage());
									finalDto.setMinRateEditable(resDto.getMinRateEditable());
									finalDto.setMaxRateEditable(resDto.getMaxRateEditable());
									finalDto.setServiceRate(resDto.getServiceRate()!=null && resDto.getServiceRate()>0 ? resDto.getServiceRate() : rate);
									finalDto.setFinalPrice(resDto.getFinalPrice()!=null && resDto.getFinalPrice()>0 ? resDto.getFinalPrice() : netAmt);
									finalDto.setNetAmount(resDto.getFinalPrice()!=null && resDto.getFinalPrice()>0 ? resDto.getFinalPrice() : netAmt);
									finalDto.setOrdTotalAmount(resDto.getServiceRate()!=null && resDto.getServiceRate()>0 ? resDto.getServiceRate() : rate);
									finalDto.setCreaditPayable(totalCreditPayable);
									finalDto.setSelfPayable(totalSelfPayable);
									finalDto.setConcession(concession);
									finalDto.setPayeeId(resDto.getPayeeId());
									finalDto.setSelfPayeeId(resDto.getSelfPayeeId());
									finalDto.setServiceId(serviceId);
										detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
											detailsPayeeMapperDto.setPayeeId(resDto.getSelfPayeeId());
											detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(totalSelfPayable));
											detailsPayeeMapperDto.setServiceId(serviceId);
											detailsPayeeMapperDto.setIsRateEditable('N');
											detailsPayeeMapperDto.setCoShare(0.00);
										orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
									finalDto.setCoShare(resDto.getCoShare()!=null ? resDto.getCoShare() : 0.00);
									finalDto.setTaxPercentage(resDto.getTaxPercentage());
									finalDto.setContractId(resDto.getContractId());
									finalDto.setPatientClassPercentage(resDto.getPatientClassPercentage());
									finalDto.setMarkupStatus(resDto.getMarkupStatus());
									finalDto.setSpecialityId(specialityId);
									finalDto.setSubSpecialityId(subSpecialityId);
									finalDto.setIsRateEditable(isRateEditable!=null ? isRateEditable : 'N');
									//finalDto.setIsRateEditableService(isRateEditableService!=null ? isRateEditableService : 'N');
									finalDto.setIsQuantityEditable(isQuantityEditable!=null ? isQuantityEditable : 'N');
									finalDto.setTariffId(tariffId);
									finalDto.setOrderDetailsPayeeMapperDtosList(orderDetailsPayeeMapperDtosList);
									finalDto.setIsDocReq(resDto.getIsDocReq());
									finalDto.setIsRefReq(resDto.getIsRefReq());
									finalDto.setBillingBedCategoryId(consumptionDto.getBillingBedCategoryId());
									finalDto.setIsAutoRender(resDto.getIsAutoRender()!=null ? resDto.getIsAutoRender() : 'N');
								return new Response(SUCCESS,SUCCESS_CODE,null,null,finalDto);
							}
							
						}
					
					}
					
				}
				//1.1 ----->
				
				if(consumptionDto.getContractId()!=null && totalCreditPayable==0.00)
				{
					Double rateWithTax = 0.00;
					Integer fallBackTariffId =  iContractConsumptionDao.getFallBacktariff(consumptionDto.getEncounterId(),consumptionDto.getAdmissionId());
					
					if(fallBackTariffId!=null && fallBackTariffId>0)
					{
						consumptionDto.setTariffId(fallBackTariffId);
						
						UnitServiceTariffMasterDto unitServiceTariffMasterDto =new UnitServiceTariffMasterDto();
							unitServiceTariffMasterDto.setOrganizationId(consumptionDto.getOrganizationId());
							unitServiceTariffMasterDto.setUnitId(consumptionDto.getUnitId());
							unitServiceTariffMasterDto.setVisitTypeId(consumptionDto.getVisitTypeId());
							unitServiceTariffMasterDto.setBillingBedCategoryId(consumptionDto.getBillingBedCategoryId());
							unitServiceTariffMasterDto.setPatientTypeId(consumptionDto.getPatientCategoryId()!=null ? consumptionDto.getPatientCategoryId() : consumptionDto.getPatientTypeId()); 
							unitServiceTariffMasterDto.setPaymentEntitlementId(1);//Self
							unitServiceTariffMasterDto.setServiceId(serviceId);
							unitServiceTariffMasterDto.setOrderDate(consumptionDto.getCurrentDate());
							unitServiceTariffMasterDto.setTariffId(fallBackTariffId);
							unitServiceTariffMasterDto.setPatientClassId(consumptionDto.getPatientClassId());
							unitServiceTariffMasterDto = iEncounterMasterService.getBasePriceByServiceTariffMaster(unitServiceTariffMasterDto);
						
						if(unitServiceTariffMasterDto !=null)
						{
							
							if((unitServiceTariffMasterDto.getIsDiscount()!=null && unitServiceTariffMasterDto.getIsSchemeDiscount()!=null && ((unitServiceTariffMasterDto.getIsDiscount()=='Y' && unitServiceTariffMasterDto.getIsSchemeDiscount()=='Y')||(unitServiceTariffMasterDto.getIsSchemeDiscount()=='N'))))
							{
								if(consumptionDto.getPatientClassPercentage()!=null && consumptionDto.isPatientClassApplied()==false)
									concessionPer = consumptionDto.getPatientClassPercentage();
								else {
									patientClassDto = iContractConsumptionDao.getPatientClassAndPercentageByVisit(consumptionDto);	
									if(patientClassDto != null && patientClassDto.getPatientClassPercentage()!=null 
											 && patientClassDto.getMarkupStatus()==false 
											 && patientClassDto.getPatientClassPercentage()>0
											 && consumptionDto.getOrdDiscount()==0)
										concessionPer = patientClassDto.getPatientClassPercentage();
								}
							}else{
								concessionPer = 0.00;
							}
							
							if(unitServiceTariffMasterDto.getAmbulanceChargesServiceId()!= null && unitServiceTariffMasterDto.getAmbulanceChargesServiceId().equals(serviceId)){
								
								serviceAmount = consumptionDto.getChangedPrice() != null ? consumptionDto.getChangedPrice() : consumptionDto.getFinalPrice() ;
								
								rate = consumptionDto.getChangedPrice();
								if(consumptionDto.getQuantity()!=null && consumptionDto.getQuantity()>1)
								{
									rate = serviceAmount * consumptionDto.getQuantity();
								}
								concession = rate*(concessionPer>0 ? concessionPer:0)*0.01;
								consumptionDto.setConcession(concession);
								if(consumptionDto.getOrdDiscountPer()>0)
								{
									rateMinusConcession = rate - concession;
									discountAmt = rateMinusConcession*(consumptionDto.getOrdDiscountPer()*0.01);
								}
								if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
								{
									List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
									for (ContractConsumptionDto gstList : gstDetailsList) {
										if(gstList.getIsGstApplicable()==true && rate>=gstList.getGstAmountRange())
										{
											unitServiceTariffMasterDto.setTaxId(0);
											unitServiceTariffMasterDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
											rateWithTax = (rate-Math.abs(concession+discountAmt)) + ((gstList.getGstPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
										}
										else
										{
											unitServiceTariffMasterDto.setTaxId(0);
											unitServiceTariffMasterDto.setTaxPercentage(0.00);
											rateWithTax = (rate-Math.abs(concession+discountAmt)) + (0*(rate-Math.abs(concession+discountAmt)));
										}
									}
								}
								else
								{
								rateWithTax = (rate-Math.abs(concession+discountAmt)) + ((unitServiceTariffMasterDto.getTaxPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
								}
								
							}else
							if(consumptionDto.getChangedPrice()!=null && consumptionDto.getChangedPrice()>=0)
							{
								
								serviceAmount = consumptionDto.getChangedPrice();
								rate = consumptionDto.getChangedPrice();
								if(consumptionDto.getQuantity()!=null && consumptionDto.getQuantity()>1)
								{
									rate = serviceAmount * consumptionDto.getQuantity();
								}
								concession = (rate*concessionPer)*0.01;
								consumptionDto.setConcession(concession);
								if(consumptionDto.getOrdDiscountPer()>0)
								{
									rateMinusConcession = rate - concession;
									discountAmt = rateMinusConcession*(consumptionDto.getOrdDiscountPer()*0.01);
								}
								if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
								{
									List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
									for (ContractConsumptionDto gstList : gstDetailsList) {
										if(gstList.getIsGstApplicable()==true && rate>=gstList.getGstAmountRange())
										{
											unitServiceTariffMasterDto.setTaxId(0);
											unitServiceTariffMasterDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
											rateWithTax = (rate-Math.abs(concession+discountAmt)) + ((gstList.getGstPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
										}
										else
										{
											unitServiceTariffMasterDto.setTaxId(0);
											unitServiceTariffMasterDto.setTaxPercentage(0.00);
											rateWithTax = (rate-Math.abs(concession+discountAmt)) + (0*(rate-Math.abs(concession+discountAmt)));
										}
									}
								}
								else
								{	
								rateWithTax = (rate-Math.abs(concession+discountAmt)) + ((unitServiceTariffMasterDto.getTaxPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
								}
							}else {
								
								if(consumptionDto.getConsultationAmount()!=null)
									rate = consumptionDto.getConsultationAmount();
								else if(consumptionDto.getProcedureId()!=null && consumptionDto.getProcedureId()>0)
									rate = consumptionDto.getServiceRate();
								else if(consumptionDto.getIsPeriodicityService()!=null && consumptionDto.getIsPeriodicityService()=='Y')
									rate = consumptionDto.getServiceRate();
								else
									rate = (unitServiceTariffMasterDto.getRate() != null ? unitServiceTariffMasterDto.getRate() : 0.0);
								
								serviceAmount = rate;
								
								if(consumptionDto.getQuantity()!=null && consumptionDto.getQuantity()>1)
								{
									rate = rate * consumptionDto.getQuantity();
								}
								
								if(consumptionDto.getChangedPrice()==null && consumptionDto.getServiceChargeStatus()!=null && consumptionDto.getServiceChargeStatus()==0)
								{
									serviceAmount = serviceAmount/2;
									rate = rate/2;
									unitServiceTariffMasterDto.setFinalRate(unitServiceTariffMasterDto.getFinalRate()!=null && unitServiceTariffMasterDto.getFinalRate().intValue()>0 ?  unitServiceTariffMasterDto.getFinalRate()/2 : 0);
								}
							
								if(concessionPer>0)
								{
									concession = rate*(concessionPer*0.01);
								}
								if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
								{
									List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
									for (ContractConsumptionDto gstList : gstDetailsList) {
										if(gstList.getIsGstApplicable()==true && rate>=gstList.getGstAmountRange())
										{
											unitServiceTariffMasterDto.setTaxId(0);
											unitServiceTariffMasterDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
											rateWithTax = (rate-(concession+discountAmt)) + ((gstList.getGstPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
										}
										else
										{
											unitServiceTariffMasterDto.setTaxId(0);
											unitServiceTariffMasterDto.setTaxPercentage(0.00);
											rateWithTax = (rate-(concession+discountAmt)) + (0*(rate-Math.abs(concession+discountAmt)));
										}
									}
								}
								else
								{
								rateWithTax = (rate-(concession+discountAmt)) + ((unitServiceTariffMasterDto.getTaxPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
								}
							}
							
							if(rate>0)
							{
									finalDto.setTaxId(unitServiceTariffMasterDto.getTaxId());
									if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
									{
										List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
										for (ContractConsumptionDto gstList : gstDetailsList) {
											if(gstList.getIsGstApplicable()==true && rate>=gstList.getGstAmountRange())
											{
												unitServiceTariffMasterDto.setTaxId(0);
												unitServiceTariffMasterDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
												finalDto.setTaxAmt((gstList.getGstPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
											}
											else
											{
												unitServiceTariffMasterDto.setTaxId(0);
												unitServiceTariffMasterDto.setTaxPercentage(0.00);
												finalDto.setTaxAmt(0*(rate-Math.abs(concession+discountAmt)));
											}
										}
									}
									else
									{
									finalDto.setTaxAmt((unitServiceTariffMasterDto.getTaxPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
									}
									finalDto.setTaxPercentage(unitServiceTariffMasterDto.getTaxPercentage());
									finalDto.setMinRateEditable(unitServiceTariffMasterDto.getMinRateEditable());
									finalDto.setMaxRateEditable(unitServiceTariffMasterDto.getMaxRateEditable());
									finalDto.setServiceRate(serviceAmount!=null ? serviceAmount : 0.00);
									finalDto.setFinalPrice(rateWithTax!=null ? rateWithTax : 0.00);
									finalDto.setNetAmount(rateWithTax);
									finalDto.setOrdTotalAmount(rate !=null ? rate : 0.00);
									finalDto.setConcession(concession);
									finalDto.setCreaditPayable(0.00);
									finalDto.setSelfPayable(rateWithTax!=null ? rateWithTax : 0.00);
									finalDto.setServiceId(serviceId);
									finalDto.setCoShare(100.00);
										detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
											detailsPayeeMapperDto.setPayeeId(unitServiceTariffMasterDto.getSelfPayeeId());
											detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(rateWithTax!=null ? rateWithTax : 0.00));
											detailsPayeeMapperDto.setPayeeDesc("Self");
											detailsPayeeMapperDto.setServiceId(serviceId);
											detailsPayeeMapperDto.setIsRateEditable('N');
											detailsPayeeMapperDto.setCoShare(0.00);
										orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
									finalDto.setPayeeId(consumptionDto.getSelfPayeeId());
									finalDto.setSelfPayeeId(unitServiceTariffMasterDto.getSelfPayeeId());
									finalDto.setPatientClassPercentage(concessionPer);
									finalDto.setMarkupStatus(unitServiceTariffMasterDto.getMarkupStatus());
									finalDto.setIsRateEditable(unitServiceTariffMasterDto.getIsRateEditable());
									finalDto.setSpecialityId(unitServiceTariffMasterDto.getSpecialityId());
									finalDto.setSubSpecialityId(unitServiceTariffMasterDto.getSubSpecialityId());
									//finalDto.setIsRateEditableService(unitServiceTariffMasterDto.getIsRateEditableService()!=null ? unitServiceTariffMasterDto.getIsRateEditableService() : 'N');
									finalDto.setIsQuantityEditable(unitServiceTariffMasterDto.getIsQuantityEditable()!=null ? unitServiceTariffMasterDto.getIsQuantityEditable() : 'N');
									finalDto.setOrderDetailsPayeeMapperDtosList(orderDetailsPayeeMapperDtosList);
									finalDto.setIsDocReq(unitServiceTariffMasterDto.getIsDocReq());
									finalDto.setIsRefReq(unitServiceTariffMasterDto.getIsRefReq());
									finalDto.setOrdDiscount(discountAmt);
									finalDto.setBillingBedCategoryId(consumptionDto.getBillingBedCategoryId());
									finalDto.setIsAutoRender(unitServiceTariffMasterDto.getIsAutoRender()!=null ? unitServiceTariffMasterDto.getIsAutoRender() : 'N');
								return new Response(SUCCESS,SUCCESS_CODE,null,serviceDetailslist,finalDto);
							}
						}else{
							
							finalDto.setTaxAmt(0.00);
							finalDto.setOrdDiscount(0.00);
							finalDto.setServiceRate(0.00);
							finalDto.setFinalPrice(0.00);
							finalDto.setNetAmount(0.00);
							finalDto.setOrdTotalAmount(0.00);
							finalDto.setConcession(0.00);
							finalDto.setCreaditPayable(0.00);
							finalDto.setSelfPayable(0.00);
							finalDto.setOrdDiscount(0.00);
							finalDto.setServiceId(serviceId);
								detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
									detailsPayeeMapperDto.setPayeeId(consumptionDto.getSelfPayeeId());
									detailsPayeeMapperDto.setPayeeAmount(BigDecimal.ZERO);
									detailsPayeeMapperDto.setPayeeDesc("Self");
									detailsPayeeMapperDto.setServiceId(serviceId);
									detailsPayeeMapperDto.setIsRateEditable('N');
									detailsPayeeMapperDto.setCoShare(0.00);
								orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
							finalDto.setPayeeId(consumptionDto.getSelfPayeeId());
							finalDto.setPatientClassPercentage(0.00);
							finalDto.setIsRateEditable('Y');
							finalDto.setOrderDetailsPayeeMapperDtosList(orderDetailsPayeeMapperDtosList);
							finalDto.setBillingBedCategoryId(consumptionDto.getBillingBedCategoryId());
							finalDto.setIsAutoRender('N');
							finalDto.setDoesNotExist('N');
							return new Response(SUCCESS,SUCCESS_CODE,"Service is not mapped with the tariff..!!",null,finalDto);
						}
					}
				}
			}
			//1----->
			if(consumptionDto.getContractId()==null || totalCreditPayable==0.00 )
			{
				Double rateWithTax = 0.00;
				
				UnitServiceTariffMasterDto unitServiceTariffMasterDto =new UnitServiceTariffMasterDto();
					unitServiceTariffMasterDto.setOrganizationId(consumptionDto.getOrganizationId());
					unitServiceTariffMasterDto.setUnitId(consumptionDto.getUnitId());
					unitServiceTariffMasterDto.setVisitTypeId(consumptionDto.getVisitTypeId());
					unitServiceTariffMasterDto.setBillingBedCategoryId(consumptionDto.getBillingBedCategoryId());
					unitServiceTariffMasterDto.setPatientTypeId(consumptionDto.getPatientCategoryId()!=null ? consumptionDto.getPatientCategoryId() : consumptionDto.getPatientTypeId()); 
					unitServiceTariffMasterDto.setPaymentEntitlementId(1);//Self
					unitServiceTariffMasterDto.setServiceId(serviceId);
					unitServiceTariffMasterDto.setOrderDate(consumptionDto.getCurrentDate());
					unitServiceTariffMasterDto.setTariffId((consumptionDto.getTariffId()!=null && consumptionDto.getTariffId()>0)?consumptionDto.getTariffId():consumptionDto.getDefaultSelfTariffId());
					unitServiceTariffMasterDto.setPatientClassId(consumptionDto.getPatientClassId());
					unitServiceTariffMasterDto = iEncounterMasterService.getBasePriceByServiceTariffMaster(unitServiceTariffMasterDto);
				
				if(unitServiceTariffMasterDto !=null && unitServiceTariffMasterDto.getUnitServiceTriffId()!=null)
				{
					if((unitServiceTariffMasterDto.getIsDiscount()!=null && unitServiceTariffMasterDto.getIsSchemeDiscount()!=null && ((unitServiceTariffMasterDto.getIsDiscount()=='Y' && unitServiceTariffMasterDto.getIsSchemeDiscount()=='Y')||(unitServiceTariffMasterDto.getIsSchemeDiscount()=='N'))))
					{
						if(consumptionDto.getPatientClassPercentage()!=null && consumptionDto.isPatientClassApplied()==false)
							concessionPer = consumptionDto.getPatientClassPercentage();
						else  
						{
							if(consumptionDto.getIsFromEncounter()!=null) 
							{
								patientClassDto = iContractConsumptionDao.getPatientClassAndPercentageByVisitWithountEncounter(consumptionDto);
							}else if((consumptionDto.getEncounterId()==null || consumptionDto.getEncounterId()==0) && consumptionDto.getAdmissionId()==null)	
							{
								patientClassDto = iContractConsumptionDao.getPatientClassAndPercentageByVisitWithountEncounter(consumptionDto);
							}else {
								patientClassDto = iContractConsumptionDao.getPatientClassAndPercentageByVisit(consumptionDto);	
							}
							
							if(patientClassDto != null && patientClassDto.getPatientClassPercentage()!=null 
										 && patientClassDto.getMarkupStatus()==false 
										 && patientClassDto.getPatientClassPercentage()>0)
							{
								concessionPer = patientClassDto.getPatientClassPercentage();
							}
							
						}
					}else{
						concessionPer = 0.00;
					}
					
					if(unitServiceTariffMasterDto.getAmbulanceChargesServiceId()!= null && unitServiceTariffMasterDto.getAmbulanceChargesServiceId().equals(serviceId)){
						
						serviceAmount = consumptionDto.getChangedPrice() != null ? consumptionDto.getChangedPrice() : consumptionDto.getFinalPrice() ;
						
						rate = serviceAmount;
						if(consumptionDto.getQuantity()!=null && consumptionDto.getQuantity()>1)
						{
							rate = serviceAmount * consumptionDto.getQuantity();
						}
						concession = rate*(concessionPer>0 ? concessionPer:0)*0.01;
						consumptionDto.setConcession(concession);
						if(consumptionDto.getOrdDiscountPer()>0)
						{
							rateMinusConcession = rate - concession;
							discountAmt = rateMinusConcession*(consumptionDto.getOrdDiscountPer()*0.01);
						}
						if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
						{
							List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
							for (ContractConsumptionDto gstList : gstDetailsList) {
								if(gstList.getIsGstApplicable()==true && rate>=gstList.getGstAmountRange())
								{
									unitServiceTariffMasterDto.setTaxId(0);
									unitServiceTariffMasterDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
									rateWithTax = (rate-Math.abs(concession+discountAmt)) + ((gstList.getGstPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
								}
								else
								{
									unitServiceTariffMasterDto.setTaxId(0);
									unitServiceTariffMasterDto.setTaxPercentage(0.00);
									rateWithTax = (rate-Math.abs(concession+discountAmt)) + (0*(rate-Math.abs(concession+discountAmt)));
								}
							}
						}
						else
						{	
						rateWithTax = (rate-Math.abs(concession+discountAmt)) + ((unitServiceTariffMasterDto.getTaxPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
						}
						
					}else if(consumptionDto.getChangedPrice()!=null && consumptionDto.getChangedPrice()>=0)
					{
						serviceAmount = consumptionDto.getChangedPrice();
						
						rate = consumptionDto.getChangedPrice();
						if(consumptionDto.getQuantity()!=null && consumptionDto.getQuantity()>1)
						{
							rate = serviceAmount * consumptionDto.getQuantity();
						}
						concession = rate*(concessionPer>0 ? concessionPer:0)*0.01;
						consumptionDto.setConcession(concession);
						if(consumptionDto.getOrdDiscountPer()>0)
						{
							rateMinusConcession = rate - concession;
							discountAmt = rateMinusConcession*(consumptionDto.getOrdDiscountPer()*0.01);
						}
						if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
						{
							List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
							for (ContractConsumptionDto gstList : gstDetailsList) {
								if(gstList.getIsGstApplicable()==true && rate>=gstList.getGstAmountRange())
								{
									unitServiceTariffMasterDto.setTaxId(0);
									unitServiceTariffMasterDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
									rateWithTax = (rate-Math.abs(concession+discountAmt)) + ((gstList.getGstPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
								}
								else
								{
									unitServiceTariffMasterDto.setTaxId(0);
									unitServiceTariffMasterDto.setTaxPercentage(0.00);
									rateWithTax = (rate-Math.abs(concession+discountAmt)) + (0*(rate-Math.abs(concession+discountAmt)));
								}
							}
						}
						else
						{	
						rateWithTax = (rate-Math.abs(concession+discountAmt)) + ((unitServiceTariffMasterDto.getTaxPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
						}
						
					}else {
						
						if(consumptionDto.getConsultationAmount()!=null)
							rate = consumptionDto.getConsultationAmount();
						else if(consumptionDto.getProcedureId()!=null && consumptionDto.getProcedureId()>0)
							rate = consumptionDto.getServiceRate();
						else if(consumptionDto.getIsPeriodicityService()!=null && consumptionDto.getIsPeriodicityService()=='Y')
							rate = consumptionDto.getServiceRate();
						else
							rate = (unitServiceTariffMasterDto.getRate() != null ? unitServiceTariffMasterDto.getRate() : 0.0);
						
						serviceAmount = rate;
						
						if(consumptionDto.getQuantity()!=null && consumptionDto.getQuantity()>1)
						{
							rate = serviceAmount * consumptionDto.getQuantity();
						}
						
						if(consumptionDto.getChangedPrice()==null && consumptionDto.getServiceChargeStatus()!=null && consumptionDto.getServiceChargeStatus()==0)
						{
							serviceAmount = serviceAmount/2;
							rate = rate/2;
							unitServiceTariffMasterDto.setFinalRate(unitServiceTariffMasterDto.getFinalRate()!=null && unitServiceTariffMasterDto.getFinalRate().intValue()>0 ?  unitServiceTariffMasterDto.getFinalRate()/2 : 0);
						}
					
						if(concessionPer > 0.00)
						{
							concession = rate*(concessionPer*0.01);
						}
						
						if(consumptionDto.getOrdDiscountPer()>0)
						{
							rateMinusConcession = rate - concession;
							discountAmt = rateMinusConcession*(consumptionDto.getOrdDiscountPer()*0.01);
						}
						if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
						{
							List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
							for (ContractConsumptionDto gstList : gstDetailsList) {
								if(gstList.getIsGstApplicable()==true && rate>=gstList.getGstAmountRange())
								{
									unitServiceTariffMasterDto.setTaxId(0);
									unitServiceTariffMasterDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
									rateWithTax = (rate-Math.abs(concession+discountAmt)) + ((gstList.getGstPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
								}
								else
								{
									unitServiceTariffMasterDto.setTaxId(0);
									unitServiceTariffMasterDto.setTaxPercentage(0.00);
									rateWithTax = (rate-Math.abs(concession+discountAmt)) + (0*(rate-Math.abs(concession+discountAmt)));
								}
							}
						}
						else
						{
						rateWithTax = (rate-Math.abs(concession+discountAmt)) + ((unitServiceTariffMasterDto.getTaxPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
						}
					}
						
					finalDto.setTaxId(unitServiceTariffMasterDto.getTaxId());
					finalDto.setOrdDiscount(discountAmt);
					if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
					{
						List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
						for (ContractConsumptionDto gstList : gstDetailsList) {
							if(gstList.getIsGstApplicable()==true && rate>=gstList.getGstAmountRange())
							{
								unitServiceTariffMasterDto.setTaxId(0);
								unitServiceTariffMasterDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
								finalDto.setTaxAmt((gstList.getGstPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
							}
							else
							{
								unitServiceTariffMasterDto.setTaxId(0);
								unitServiceTariffMasterDto.setTaxPercentage(0.00);
								finalDto.setTaxAmt(0*(rate-Math.abs(concession+discountAmt)));
							}
						}
					}
					else
					{
					finalDto.setTaxAmt((unitServiceTariffMasterDto.getTaxPercentage()*0.01)*(rate-Math.abs(concession+discountAmt)));
					}
					finalDto.setTaxPercentage(unitServiceTariffMasterDto.getTaxPercentage()!=null?unitServiceTariffMasterDto.getTaxPercentage():00);
					finalDto.setSpecialityName(unitServiceTariffMasterDto.getSpecialityDesc());
					finalDto.setServiceName(unitServiceTariffMasterDto.getServiceDesc());
					finalDto.setMinRateEditable(unitServiceTariffMasterDto.getMinRateEditable());
					finalDto.setMaxRateEditable(unitServiceTariffMasterDto.getMaxRateEditable());
					finalDto.setServiceRate(serviceAmount !=null ? serviceAmount : 0.00);
					finalDto.setFinalPrice(rateWithTax!=null ? rateWithTax : 0.00);
					finalDto.setNetAmount(rateWithTax);
					finalDto.setOrdTotalAmount(rate !=null ? rate : 0.00);
					finalDto.setConcession(concession);
					finalDto.setCreaditPayable(0.00);
					finalDto.setCoShare(100.00);
					finalDto.setSelfPayable(rateWithTax!=null ? rateWithTax : 0.00);
					finalDto.setSpecialityId(unitServiceTariffMasterDto.getSpecialityId());
					finalDto.setSubSpecialityId(unitServiceTariffMasterDto.getSubSpecialityId());
					finalDto.setServiceId(serviceId);
						detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
							detailsPayeeMapperDto.setPayeeId(unitServiceTariffMasterDto.getSelfPayeeId());
							detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(rateWithTax!=null ? rateWithTax : 0.00));
							detailsPayeeMapperDto.setPayeeDesc("Self");
							detailsPayeeMapperDto.setServiceId(serviceId);
							detailsPayeeMapperDto.setIsRateEditable('N');
							detailsPayeeMapperDto.setCoShare(0.00);
						orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
					finalDto.setPayeeId(unitServiceTariffMasterDto.getSelfPayeeId());
					finalDto.setSelfPayeeId(unitServiceTariffMasterDto.getSelfPayeeId());
					finalDto.setPatientClassPercentage(concessionPer);
					finalDto.setMarkupStatus(unitServiceTariffMasterDto.getMarkupStatus());
					finalDto.setIsRateEditable(unitServiceTariffMasterDto.getIsRateEditable());
					finalDto.setSpecialityId(unitServiceTariffMasterDto.getSpecialityId());
					finalDto.setSubSpecialityId(unitServiceTariffMasterDto.getSubSpecialityId());
					//finalDto.setIsRateEditable(unitServiceTariffMasterDto.getIsRateEditableService()!=null ? unitServiceTariffMasterDto.getIsRateEditableService() : 'N');
					finalDto.setIsQuantityEditable(unitServiceTariffMasterDto.getIsQuantityEditable()!=null ? unitServiceTariffMasterDto.getIsQuantityEditable() : 'N');
					finalDto.setIsDocReq(unitServiceTariffMasterDto.getIsDocReq());
					finalDto.setIsRefReq(unitServiceTariffMasterDto.getIsRefReq());
					finalDto.setOrderDetailsPayeeMapperDtosList(orderDetailsPayeeMapperDtosList);
					finalDto.setBillingBedCategoryId(consumptionDto.getBillingBedCategoryId());
					finalDto.setIsAutoRender(unitServiceTariffMasterDto.getIsAutoRender()!=null ? unitServiceTariffMasterDto.getIsAutoRender() : 'N');
					return new Response(SUCCESS,SUCCESS_CODE,null,serviceDetailslist,finalDto);
				}else{
						finalDto.setTaxAmt(0.00);
						finalDto.setServiceRate(0.00);
						finalDto.setFinalPrice(0.00);
						finalDto.setNetAmount(0.00);
						finalDto.setOrdTotalAmount(0.00);
						finalDto.setConcession(0.00);
						finalDto.setCreaditPayable(0.00);
						finalDto.setSelfPayable(0.00);
						finalDto.setOrdDiscount(0.00);
						finalDto.setCoShare(100.00);
						finalDto.setTaxPercentage(0.00);
						finalDto.setServiceId(serviceId);
							detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
								detailsPayeeMapperDto.setPayeeId(consumptionDto.getSelfPayeeId());
								detailsPayeeMapperDto.setPayeeAmount(BigDecimal.ZERO);
								detailsPayeeMapperDto.setPayeeDesc("Self");
								detailsPayeeMapperDto.setServiceId(serviceId);
								detailsPayeeMapperDto.setIsRateEditable('N');
								detailsPayeeMapperDto.setCoShare(0.00);
							orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
						finalDto.setPayeeId(consumptionDto.getSelfPayeeId());
						finalDto.setPatientClassPercentage(0.00);
						finalDto.setIsRateEditable('Y');
						finalDto.setOrderDetailsPayeeMapperDtosList(orderDetailsPayeeMapperDtosList);
						finalDto.setBillingBedCategoryId(consumptionDto.getBillingBedCategoryId());
						finalDto.setIsAutoRender('N');
						finalDto.setDoesNotExist('N');
					return new Response(SUCCESS,SUCCESS_CODE,"Service is not mapped with the tariff..!!",null,finalDto);
				}
				
			}	
			
		}catch(Exception e){
			
		}
		return new Response<>(ERROR, COMMON_ERROR_CODE, COMMON_ERROR_MESSAGE, null, null);
	}
	
	@Override
	@Transactional
	public Response consumeServiceFromContractPackage(ContractConsumptionDto consumptionDto,List<ContractConsumptionDto>  contractConsumptionList) {
		try{
			if(consumptionDto.getVisitTypeId()==1)
				consumptionDto.setAdmissionId(null);
			if(consumptionDto.getVisitTypeId()==2 || consumptionDto.getVisitTypeId()==3 || consumptionDto.getVisitTypeId()==4)
				consumptionDto.setEncounterId(null);
			
			Integer priorityId = 0;
			Character isRateEditable = 'N';
			Character isRateEditableService = 'N';
			Character isQuantityEditable='N';
			Integer count = 0;
			Double rate = null;
			Double selfPayable = 0.00;
			Double creaditPayable = 0.00;
			List<ContractConsumptionDto> serviceDetailslist = null;
			OrderDetailsPayeeMapperDto detailsPayeeMapperDto = null;
			List<OrderDetailsPayeeMapperDto> orderDetailsPayeeMapperDtosList = new LinkedList<>();
			Double taxAmt = 0.0;
			Double taxPer = 0.0;
			Integer taxId = null;
			Double servicerate = 0.0;
			Double totalCreditPayable = 0.00;
			Double totalSelfPayable = 0.00;
			Double coShareCreaditPayable = 0.00;
			Double preAuthAmount = 0.00;
			Integer specialityId = null;
			Integer tariffId = null;
			ContractConsumptionDto finalDto = new ContractConsumptionDto();
			ContractConsumptionDto patientClassDto = null;
			Double concession = 0.00;
			Double concessionPer = 0.00;
			//<-----1. Check if an encounter or admission has any contract or not.
			
			Tax tax = iEncounterMasterDao.getTaxPercentageByServiceId(consumptionDto.getServiceId(),consumptionDto.getUnitId());
			if(tax!=null) {
				taxPer=tax.getTaxPercentage();
			    taxId =tax.getId();}
			else
				taxPer = 0.00;
				
			
			if(consumptionDto.getContractId()!=null && consumptionDto.getContractId().intValue()>0 && consumptionDto.getSelfPayable()>0)
			{
				//<-----1.1 Get the service rates from all the sponsors (list is fetched according to the highest priority)
				serviceDetailslist =  contractConsumptionList !=null ? contractConsumptionList : iContractConsumptionDao.getServiceDetailsByContractId(consumptionDto);
				for(ContractConsumptionDto resDto : serviceDetailslist)
				{	
					Character isBedService=iEncounterMasterDao.getIsBedChargesService(consumptionDto);
					if(isBedService==null)
					{
						isBedService='N';
					}
					consumptionDto.setIsBedService(isBedService);
					boolean gstApplicableForBillingBedCategory=iContractConsumptionDao.getGstApplicableForBillingBedCategory(consumptionDto.getUnitId());
					if(gstApplicableForBillingBedCategory==true && consumptionDto.getIsBedService().equals('Y'))
					{
						List<ContractConsumptionDto> gstDetailsList=iContractConsumptionDao.getgstDetails(consumptionDto.getBillingBedCategoryId());
						for (ContractConsumptionDto gstList : gstDetailsList) {
							if(gstList.getIsGstApplicable()==true && resDto.getServiceRate()>=gstList.getGstAmountRange())
							{
								resDto.setTaxId(0);
								resDto.setTaxPercentage(gstList.getGstPercentage().doubleValue());
								taxPer=gstList.getGstPercentage().doubleValue();
								taxId=0;
								consumptionDto.setTaxId(taxId);
								consumptionDto.setTaxPercentage(taxPer);
							}
							else
							{
								resDto.setTaxId(0);
								taxPer=0.00;
								resDto.setTaxPercentage(0.00);
								taxId=0;
								consumptionDto.setTaxId(taxId);
								consumptionDto.setTaxPercentage(0.00);
							}
						}
					}
					if(resDto.getType()==3 || resDto.getType()==4)
                        resDto.setType(2);
                    if(consumptionDto.getCurrentDate()==null)
                        consumptionDto.setCurrentDate(GlobalCommonDateUtils.getStringDate(TimeZoneComponent.getDateByzone(consumptionDto.getUnitId()), "yyyy-MM-dd HH:mm:ss"));
                    
                    taxPer=resDto.getTaxPercentage();
					tariffId = resDto.getTariffId();
					resDto.setEncounterId(consumptionDto.getEncounterId());
					resDto.setAdmissionId(consumptionDto.getAdmissionId());
					specialityId = resDto.getSpecialityId();
					//<-----1.2 First check will be from the highest priority's sponsors/payer 
					if(totalCreditPayable==0)
					{
						priorityId = resDto.getPriorityId();
						isRateEditable = resDto.getIsRateEditable();
						isRateEditableService = resDto.getIsRateEditable();
						isQuantityEditable = resDto.getIsQuantityEditable();
						preAuthAmount = resDto.getPreAuthBalAmt();
						if(resDto!=null)
						{
							//Type decides whether data has came from service contract or service cap
							//<-----1.2.1 if data has came from service contract
							if(resDto.getType()==1)
							{
								if((resDto.getIsDiscount()=='Y' && resDto.getIsSchemeDiscount()=='Y')||(resDto.getIsSchemeDiscount()=='N'))
								{
									if(consumptionDto.getPatientClassPercentage()!=null && consumptionDto.isPatientClassApplied()==false)
										concessionPer = consumptionDto.getPatientClassPercentage();
									else {
										patientClassDto = iContractConsumptionDao.getPatientClassAndPercentageByVisit(consumptionDto);
										
										if(patientClassDto !=null && patientClassDto.getMarkupStatus()!=null && patientClassDto.getMarkupStatus()==false && patientClassDto.getPatientClassPercentage()!=null && patientClassDto.getPatientClassPercentage()>0)
											concessionPer = patientClassDto.getPatientClassPercentage();
									}
								}else{
									concessionPer=0.00;
								}
/*								
*/								if(concessionPer>0.00)
									concession = consumptionDto.getSelfPayable()*(concessionPer*0.01);
								else 
									concession=0.00;
									
								/* } */
								consumptionDto.setServiceRateForPackageAdd(consumptionDto.getSelfPayable());
								servicerate = consumptionDto.getSelfPayable();
								taxAmt = ((servicerate-concession)*(taxPer*0.01));
								//<-----1.2.2 If cap amount is un-defined, then the service amount included in contract will be credit payable.
								rate = (servicerate-concession) + taxAmt;
								
								if(resDto.getIsTariffRateApplicable()=='Y')
								{
									selfPayable = 0.00;
								    creaditPayable = rate;
								    
								    if(resDto.getCoShare()!=null)
									{
										coShareCreaditPayable = rate * (resDto.getCoShare()*0.01);
										creaditPayable=creaditPayable-coShareCreaditPayable;
								    	selfPayable= selfPayable+coShareCreaditPayable;
									}
								    
								}else{
									if(rate <= resDto.getCreaditPayable())
								    {
								    	selfPayable = 0.00;
									    creaditPayable = rate;
								    }
								    else {
								    	selfPayable = rate-resDto.getCreaditPayable();
									    creaditPayable = resDto.getCreaditPayable();
								    }
								}
								
								resDto.setCreaditPayable(creaditPayable);
								resDto.setSelfPayable(selfPayable);
								
								if(resDto.getBalanceCapAmount()==null)
								{
									if(preAuthAmount!=null && consumptionDto.isApplyAuth())
									{
										if(creaditPayable>preAuthAmount)
										{
											selfPayable = selfPayable + creaditPayable - preAuthAmount;
											creaditPayable = preAuthAmount;
										}
										if(consumptionDto.getUpdateCap()==null && creaditPayable>0)
											iContractConsumptionDao.updatePreAuthAmount(resDto.getPreauthId(),creaditPayable);
									}
									
									totalCreditPayable = totalCreditPayable+creaditPayable;
									totalSelfPayable = totalSelfPayable+selfPayable;
								}
								
								
								//1.2.2 ----->
								
								//<-----1.2.3 If cap amount is defined
								if(resDto.getBalanceCapAmount()!=null)
								{
									//<---1.2.3.1 If cap amount is greater than credit payable amount then assign the self and credit values as it is
									if(resDto.getCreaditPayable() < resDto.getBalanceCapAmount())
									{
										selfPayable = resDto.getSelfPayable();
										creaditPayable =  resDto.getCreaditPayable();
									}
									//1.2.3.1--->
									
									//<---1.2.3.2 Else whatever amount remaining in the cap will be credit payable and rest of the amount will be self payable
									else
									{
										selfPayable = resDto.getSelfPayable()+(resDto.getCreaditPayable()-resDto.getBalanceCapAmount());
										creaditPayable = resDto.getBalanceCapAmount();
									}
									//1.2.3.2 --->
								
									if(resDto.getCoShare()!=null && resDto.getCoShare()>0)
									{
										if(resDto.getCoShare()!=null)
										{
											coShareCreaditPayable = creaditPayable * (resDto.getCoShare()*0.01);
											creaditPayable=creaditPayable-coShareCreaditPayable;
									    	selfPayable= selfPayable+coShareCreaditPayable;
										}
									}
									
									if(preAuthAmount!=null && consumptionDto.isApplyAuth())
									{
										if(creaditPayable>preAuthAmount)
										{
											selfPayable = selfPayable + creaditPayable - preAuthAmount;
											creaditPayable = preAuthAmount;
										}
										if(consumptionDto.getUpdateCap()==null && creaditPayable>0)
											iContractConsumptionDao.updatePreAuthAmount(resDto.getPreauthId(),creaditPayable);
									}
									
									
									//<---1.2.3.3 update cap amount
									if(creaditPayable>0)
									{
											consumptionDto.setSpecialityId(resDto.getSpecialityId());
											consumptionDto.setContractId(resDto.getContractId());
										iContractConsumptionDao.updateCapAmount(consumptionDto,creaditPayable); 
									}
									//1.2.3.3--->
									
									totalCreditPayable = totalCreditPayable+creaditPayable;
									totalSelfPayable = totalSelfPayable+selfPayable;
								}
								//1.2.3----->
							}
							//1.2.1----->
							
							//<-----1.2.2 if data has came from service cap
							if(resDto.getType()==2)
							{
								//<-----1.2.2.1 check the applicable days lies between the date of admission and current date's difference
								if(consumptionDto.getAdmissionId()!=null)
									resDto.setDayDiff(GlobalCommonDateUtils.getDateDifference(GlobalCommonDateUtils.getDate(consumptionDto.getCurrentDate().substring(0,10),"yyyy-MM-dd"), GlobalCommonDateUtils.getDate(resDto.getDoa().substring(0,10),"yyyy-MM-dd"))+1);
								
								if(resDto.getTillDischarge()==true||resDto.getApplicableDays()>=resDto.getDayDiff())
								{
									resDto.setType(3);
									if(resDto.getApplicableDate()!=null && !(resDto.getApplicableDate().equals(consumptionDto.getCurrentDate().substring(0,10))))
									{
											resDto.setType(4);
											resDto.setBalanceAmount(resDto.getAmount());
											resDto.setBalanceQuantity(resDto.getQuantity());
											resDto.setApplicableDate(consumptionDto.getCurrentDate().substring(0,10));
									}
									
									//rate = consumptionDto.getSelfPayable();
									
									if(resDto.getIsTariffRateApplicable()=='Y')
									{
										patientClassDto = iContractConsumptionDao.getPatientClassAndPercentageByVisit(consumptionDto);
										if(patientClassDto.getMarkupStatus()!=null && patientClassDto.getMarkupStatus()==false && patientClassDto.getPatientClassPercentage()!=null && patientClassDto.getPatientClassPercentage()>0 && consumptionDto.getOrdDiscountPer()==0)
										{
											concession = consumptionDto.getSelfPayable()*(patientClassDto.getPatientClassPercentage()*0.01);
										}
										else {
											concession =0.00;
										}
									}
									
									servicerate = consumptionDto.getSelfPayable();
									taxAmt = ((servicerate-concession)*(taxPer*0.01));
									rate = servicerate + taxAmt - concession;
									
									//<-----1.2.2.1.2 If service capping is based on amount
									if(resDto.getBalanceAmount()!=null && resDto.getBalanceAmount()>0)
									{
										//<---1.2.2.1.2.1 If cap amount is greater than service rates then assign the rates to credit payable
										if(rate < resDto.getBalanceAmount())
										{
											creaditPayable = rate;
											selfPayable = 0.0;
										}
										//1.2.2.1.2.1--->
										
										//<---1.2.2.1.2.2 Else whatever amount remaining in the cap will be credit payable rest o the amount will be self payable
										else
										{
											selfPayable=rate-resDto.getBalanceAmount();
											creaditPayable=resDto.getBalanceAmount();
										}
										//1.2.2.1.2.2--->
										
										
										if(preAuthAmount!=null && consumptionDto.isApplyAuth())
										{
											if(creaditPayable>preAuthAmount)
											{
												selfPayable = selfPayable + creaditPayable - preAuthAmount;
												creaditPayable = preAuthAmount;
											}
											if(consumptionDto.getUpdateCap()==null && creaditPayable>0)
												iContractConsumptionDao.updatePreAuthAmount(resDto.getPreauthId(),creaditPayable);
										}
										
										
										//<---1.2.2.1.2.3 update cap amount
										if(consumptionDto.getUpdateCap()==null && creaditPayable>0)
											iContractConsumptionDao.updateServiceCapAmount(resDto,creaditPayable.doubleValue(),null); 
										//1.2.2.1.2.3--->
										totalCreditPayable = totalCreditPayable+creaditPayable;
										totalSelfPayable = totalSelfPayable+selfPayable;
									}
									//1.2.2.1.2----->
									
									//<-----1.2.2.1.3 If service capping is based on quantity
									if(resDto.getBalanceQuantity()!=null)
									{
										//<---1.2.2.1.3.1 If quantity exists then service rates will be assigned to credit payable
										if(resDto.getBalanceQuantity()>0)
										{
											creaditPayable = rate;
											selfPayable = 0.0;
											
											if(preAuthAmount!=null && consumptionDto.isApplyAuth())
											{
												if(creaditPayable>preAuthAmount)
												{
													selfPayable = selfPayable + creaditPayable - preAuthAmount;
													creaditPayable = preAuthAmount;
												}
												if(consumptionDto.getUpdateCap()==null && creaditPayable>0)
													iContractConsumptionDao.updatePreAuthAmount(resDto.getPreauthId(),creaditPayable);
											}
											
											//<---1.2.2.1.3.1.1 update quantity
											if(consumptionDto.getUpdateCap()==null && creaditPayable > 0)
											{
												iContractConsumptionDao.updateServiceCapAmount(resDto,null,1.00); 
											}
											
											totalCreditPayable = totalCreditPayable+creaditPayable;
											totalSelfPayable = totalSelfPayable+selfPayable;
										}
										//1.2.2.1.3.1--->
										
										//<---1.2.2.1.3.2 If quantity exceeds, then service rates will be assigned to self payable
										else
										{
											creaditPayable = 0.0;
											selfPayable =rate;
											
											totalCreditPayable = totalCreditPayable+creaditPayable;
											totalSelfPayable = totalSelfPayable+selfPayable;
										}
										//1.2.2.1.3.2--->
									}
									//1.2.2.1.3----->
								}
								//1.2.2.1----->
							}
							//1.2.2----->
						}
						if(creaditPayable>0)
						{
							detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
								detailsPayeeMapperDto.setPayeeId(resDto.getPayeeId());
								detailsPayeeMapperDto.setAssociateCompanyId(resDto.getAssociateCompanyId());
								detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(creaditPayable));
								detailsPayeeMapperDto.setContractId(resDto.getContractId());
								detailsPayeeMapperDto.setServiceId(resDto.getServiceId());
								detailsPayeeMapperDto.setPayeeDesc(resDto.getCompanyName());
								detailsPayeeMapperDto.setIsRateEditable(isRateEditable);
								detailsPayeeMapperDto.setCoShare(resDto.getCoShare());
								detailsPayeeMapperDto.setSpiltBillAmt(new BigDecimal(creaditPayable));
							orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
						}
					}
					//1.2----->
					
					//<-----1.3 Second round, after consuming the service from the highest priority's payer, if there is a still self payable component exist then check with the next sponsor/payer
					if(totalSelfPayable>0)
					{
						List<ContractConsumptionDto> serviceDetailslistsecondRoundList= serviceDetailslist;
						for(ContractConsumptionDto secondRound : serviceDetailslistsecondRoundList)
						{
							Double creaditPayableSecondRound = 0.00;
							Double secondRounfCoShare = 0.00;
							if(secondRound.getPriorityId() > priorityId)
							{
								
								secondRound.setEncounterId(consumptionDto.getEncounterId());
								secondRound.setAdmissionId(consumptionDto.getAdmissionId());
								
								if(secondRound.getType()==1)	
								{	
									consumptionDto.setSpecialityId(secondRound.getSpecialityId());
									if(secondRound.getBalanceCapAmount()==null)
									{
										if(secondRound.getCreaditPayable() > totalSelfPayable)
											creaditPayableSecondRound = totalSelfPayable;
										else
											creaditPayableSecondRound = secondRound.getCreaditPayable();
										
									}
									if(secondRound.getBalanceCapAmount()!=null)
									{
										if(secondRound.getCreaditPayable() >= totalSelfPayable)
										{
											if(totalSelfPayable < secondRound.getBalanceCapAmount())
												creaditPayableSecondRound=totalSelfPayable;
											else
												creaditPayableSecondRound=secondRound.getBalanceCapAmount();
										}
										else if(secondRound.getCreaditPayable() < totalSelfPayable)
										{
											if(secondRound.getCreaditPayable() < secondRound.getBalanceCapAmount())
												creaditPayableSecondRound=secondRound.getCreaditPayable();
											else
												creaditPayableSecondRound=secondRound.getBalanceCapAmount();
										}
									}
									
									totalCreditPayable = totalCreditPayable+creaditPayableSecondRound;
									totalSelfPayable = totalSelfPayable-creaditPayableSecondRound;
									
									if(creaditPayableSecondRound>0)
									{
										if(secondRound.getCoShare()!=null && secondRound.getCoShare()>0)
										{
											secondRounfCoShare  = ((secondRound.getCoShare()*0.01)*creaditPayableSecondRound);
											creaditPayableSecondRound = creaditPayableSecondRound - secondRounfCoShare ;
											totalSelfPayable = totalSelfPayable+secondRounfCoShare;
											totalCreditPayable =  totalCreditPayable-secondRounfCoShare;
										}
										
										if(preAuthAmount!=null && consumptionDto.isApplyAuth())
										{
											if(creaditPayableSecondRound>preAuthAmount)
											{
												double diff = creaditPayableSecondRound - preAuthAmount;
												creaditPayableSecondRound = preAuthAmount;
												
												totalSelfPayable = totalSelfPayable+diff;
												totalCreditPayable =  totalCreditPayable-diff;
											}
											if(consumptionDto.getUpdateCap()==null && creaditPayableSecondRound>0)
												iContractConsumptionDao.updatePreAuthAmount(secondRound.getPreauthId(),creaditPayableSecondRound);	
										}
										
										consumptionDto.setContractId(secondRound.getContractId());
										if(consumptionDto.getUpdateCap()==null && secondRound.getBalanceCapAmount()!=null && creaditPayableSecondRound>0)
											iContractConsumptionDao.updateCapAmount(consumptionDto,creaditPayableSecondRound);
									}
									
								}
								
								if(secondRound.getType()==2)
								{
									
									if(consumptionDto.getAdmissionId()!=null)
										resDto.setDayDiff(GlobalCommonDateUtils.getDateDifference(GlobalCommonDateUtils.getDate(consumptionDto.getCurrentDate().substring(0,10),"yyyy-MM-dd"), GlobalCommonDateUtils.getDate(secondRound.getDoa().substring(0,10),"yyyy-MM-dd"))+1);
									
									if(secondRound.getTillDischarge()==true||secondRound.getApplicableDays()>=secondRound.getDayDiff())
									{
										secondRound.setType(3);
										if(secondRound.getApplicableDate()!=null && !(secondRound.getApplicableDate().equals(consumptionDto.getCurrentDate().substring(0,10))))
										{
											secondRound.setType(4);
											secondRound.setBalanceAmount(secondRound.getAmount());
											secondRound.setBalanceQuantity(secondRound.getQuantity());
											secondRound.setApplicableDate(consumptionDto.getCurrentDate().substring(0,10));
										}
										if((secondRound.getBalanceAmount()!=null && secondRound.getBalanceAmount()>0)||secondRound.getBalanceQuantity()!=null)
										{
										
											Double balCap = secondRound.getBalanceAmount();
											if(balCap!=null && balCap>0.00)
											{
												if(totalSelfPayable <= balCap)
												{
													creaditPayableSecondRound = totalSelfPayable;
												}else{
													creaditPayableSecondRound=balCap;
												}
												
												totalCreditPayable = totalCreditPayable+creaditPayableSecondRound;
												totalSelfPayable = totalSelfPayable-creaditPayableSecondRound;
												
												if(preAuthAmount!=null && consumptionDto.isApplyAuth())
												{
													if(creaditPayableSecondRound>preAuthAmount)
													{
														double diff = creaditPayableSecondRound - preAuthAmount;
														creaditPayableSecondRound = preAuthAmount;
														
														totalSelfPayable = totalSelfPayable+diff;
														totalCreditPayable =  totalCreditPayable-diff;
													}
													if(consumptionDto.getUpdateCap()==null && creaditPayableSecondRound>0)
														iContractConsumptionDao.updatePreAuthAmount(secondRound.getPreauthId(),creaditPayableSecondRound);
												}
												
												if(consumptionDto.getUpdateCap()==null && creaditPayableSecondRound.doubleValue()>0.00)
												{
													iContractConsumptionDao.updateServiceCapAmount(secondRound,creaditPayableSecondRound.doubleValue(),null); 
												}
												
											}
											if(secondRound.getBalanceQuantity()!=null && secondRound.getBalanceQuantity()>0)
											{
													
												rate = consumptionDto.getSelfPayable();
												orderDetailsPayeeMapperDtosList=new LinkedList<>();
												if(secondRound.getBalanceQuantity()>0)
												{
													orderDetailsPayeeMapperDtosList=new LinkedList<>();
													
													creaditPayableSecondRound = rate;
													
													totalCreditPayable = rate;
													totalSelfPayable = 0.0;
													
													if(preAuthAmount!=null && consumptionDto.isApplyAuth())
													{
														if(creaditPayableSecondRound>preAuthAmount)
														{
															double diff = creaditPayableSecondRound - preAuthAmount;
															creaditPayableSecondRound = preAuthAmount;
															
															totalSelfPayable = totalSelfPayable+diff;
															totalCreditPayable =  totalCreditPayable-diff;
														}
														if(consumptionDto.getUpdateCap()==null && creaditPayableSecondRound>0)
															iContractConsumptionDao.updatePreAuthAmount(secondRound.getPreauthId(),creaditPayableSecondRound);
													}
													if(consumptionDto.getUpdateCap()==null && creaditPayableSecondRound > 0)
													{
														iContractConsumptionDao.updateServiceCapAmount(secondRound,null,1.00); 
													}
												}
											}
										}
									}
								}
							
								if(creaditPayableSecondRound>0)
								{
									detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
										detailsPayeeMapperDto.setPayeeId(secondRound.getPayeeId());
										detailsPayeeMapperDto.setAssociateCompanyId(secondRound.getAssociateCompanyId());
										detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(creaditPayableSecondRound));
										detailsPayeeMapperDto.setContractId(secondRound.getContractId());
										detailsPayeeMapperDto.setPayeeDesc(secondRound.getCompanyName());
										detailsPayeeMapperDto.setServiceId(secondRound.getServiceId());
										detailsPayeeMapperDto.setIsRateEditable('N');
										detailsPayeeMapperDto.setCoShare(secondRound.getCoShare());
										detailsPayeeMapperDto.setSpiltBillAmt(new BigDecimal(creaditPayableSecondRound));
									orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
								}
							
							}	
						count++;
						}
							
					}
					//1.3----->
					
					if((totalSelfPayable==0 && totalCreditPayable>0) || serviceDetailslist.size()==count)
					{	
						consumptionDto.setFinalPrice(rate);
						consumptionDto.setServiceRate(consumptionDto.getServiceRate());
						consumptionDto.setNetAmount(rate);
						consumptionDto.setTaxAmt(taxAmt);
						consumptionDto.setTaxId(taxId);
						consumptionDto.setCreaditPayable(totalCreditPayable);
						consumptionDto.setSelfPayable(totalSelfPayable);
						consumptionDto.setPayeeId(resDto.getPayeeId());
						consumptionDto.setConcession(concession);
						consumptionDto.setPatientClassPercentage(concessionPer);
							detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
								detailsPayeeMapperDto.setPayeeId(resDto.getSelfPayeeId());
								detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(totalSelfPayable));
								detailsPayeeMapperDto.setServiceId(resDto.getServiceId());
								detailsPayeeMapperDto.setIsRateEditable('N');
								detailsPayeeMapperDto.setCoShare(0.00);
							orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
							consumptionDto.setContractId(resDto.getContractId());
							consumptionDto.setOrderDetailsPayeeMapperDtosList(orderDetailsPayeeMapperDtosList);
						
						if(consumptionDto.getOrderDetailsPackageMapperDtosList()!=null)
							consumptionDto.setOrderDetailsPackageMapperDtosList(consumptionDto.getOrderDetailsPackageMapperDtosList());
						return new Response(SUCCESS,SUCCESS_CODE,null,null,consumptionDto);
					
					}
					
				}
				//1.1 ----->
				
			}
			//1----->
			if(consumptionDto.getContractId()==null || totalCreditPayable==0.00)
			{
				//patientClassDto = iContractConsumptionDao.getPatientClassAndPercentageByVisit(consumptionDto);
				
				UnitServiceTariffMasterDto unitServiceTariffMasterDto =new UnitServiceTariffMasterDto();
					unitServiceTariffMasterDto.setOrganizationId(consumptionDto.getOrganizationId());
					unitServiceTariffMasterDto.setUnitId(consumptionDto.getUnitId());
					unitServiceTariffMasterDto.setVisitTypeId(consumptionDto.getVisitTypeId());
					unitServiceTariffMasterDto.setBillingBedCategoryId(consumptionDto.getBillingBedCategoryId());
					unitServiceTariffMasterDto.setPatientTypeId(consumptionDto.getPatientCategoryId()!=null ? consumptionDto.getPatientCategoryId() : consumptionDto.getPatientTypeId()); 
					unitServiceTariffMasterDto.setPaymentEntitlementId(1);//Self
					unitServiceTariffMasterDto.setServiceId(consumptionDto.getServiceId());
					unitServiceTariffMasterDto.setOrderDate(consumptionDto.getCurrentDate());
					unitServiceTariffMasterDto.setTariffId((consumptionDto.getTariffId()!=null && consumptionDto.getTariffId()>0)?consumptionDto.getTariffId():consumptionDto.getDefaultSelfTariffId());
					unitServiceTariffMasterDto.setPatientClassId(consumptionDto.getPatientClassId());
				unitServiceTariffMasterDto = iEncounterMasterService.getBasePriceByServiceTariffMaster(unitServiceTariffMasterDto);
				
				if((unitServiceTariffMasterDto.getIsDiscount()=='Y' && unitServiceTariffMasterDto.getIsSchemeDiscount()=='Y')||(unitServiceTariffMasterDto.getIsSchemeDiscount()=='N'))
				{
					if(consumptionDto.getPatientClassPercentage()!=null && consumptionDto.isPatientClassApplied()==false)
						concessionPer = consumptionDto.getPatientClassPercentage();
					else{
						patientClassDto = iContractConsumptionDao.getPatientClassAndPercentageByVisit(consumptionDto);
						
						if(patientClassDto !=null && patientClassDto.getMarkupStatus()!=null && patientClassDto.getMarkupStatus()==false && patientClassDto.getPatientClassPercentage()!=null && patientClassDto.getPatientClassPercentage()>0)
							concessionPer = patientClassDto.getPatientClassPercentage();
					}
				}else{
					concessionPer= 0.00;
				}
				
				if(concessionPer > 0)
				{
					concession = consumptionDto.getSelfPayable()*(concessionPer*0.01);
				}else {
					concession = 0.00;
				}
				
				consumptionDto.setServiceRate(consumptionDto.getServiceRate());
				consumptionDto.setTaxAmt(((consumptionDto.getSelfPayable()- concession) * (taxPer / 100)));
				consumptionDto.setTaxId(taxId);
				consumptionDto.setTaxPercentage(taxPer);
				consumptionDto.setFinalPrice((consumptionDto.getSelfPayable()- concession) + consumptionDto.getTaxAmt());
				consumptionDto.setSelfPayable(consumptionDto.getFinalPrice());
				consumptionDto.setNetAmount(consumptionDto.getFinalPrice());
				consumptionDto.setCreaditPayable(0.00);
					detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
						detailsPayeeMapperDto.setPayeeId(consumptionDto.getSelfPayeeId());
						detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(consumptionDto.getSelfPayable()));
						detailsPayeeMapperDto.setPayeeDesc("Self");
						detailsPayeeMapperDto.setServiceId(consumptionDto.getServiceId());
						detailsPayeeMapperDto.setIsRateEditable('N');
						detailsPayeeMapperDto.setCoShare(0.00);
					orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
					consumptionDto.setPayeeId(consumptionDto.getSelfPayeeId());
					consumptionDto.setIsRateEditable(consumptionDto.getIsRateEditable());
					consumptionDto.setTariffId(consumptionDto.getDefaultSelfTariffId());
					consumptionDto.setOrderDetailsPayeeMapperDtosList(orderDetailsPayeeMapperDtosList);
					consumptionDto.setPatientClassPercentage(concessionPer);
					consumptionDto.setConcession(concession);
				if(consumptionDto.getOrderDetailsPackageMapperDtosList()!=null)
					consumptionDto.setOrderDetailsPackageMapperDtosList(consumptionDto.getOrderDetailsPackageMapperDtosList());
			}	
			return new Response(SUCCESS,SUCCESS_CODE,null,null,consumptionDto);
		}catch(Exception e){
			
		}
		return new Response<>(ERROR, COMMON_ERROR_CODE, COMMON_ERROR_MESSAGE, null, null);
	}
	
	
	@Override
	@Transactional
	public Response resetContractCapping(Integer encounterId, Integer admissionId) throws ApplicationException {
		try{
				iContractConsumptionDao.resetContractCapping(encounterId,admissionId);
				return new Response<>(SUCCESS, SUCCESS_CODE, COMMON_UPDATE, null, null);
		}catch(Exception e){
			
		}
		return new Response<>(ERROR, COMMON_ERROR_CODE, COMMON_ERROR_MESSAGE, null, null);
	}
	

	@Override
	@Transactional
	public Response addCapAmount(ContractConsumptionDto consumptionDto) throws ApplicationException {
		try{
			
			if(consumptionDto.getOrderDetailsId()!=null && consumptionDto.getQuantity()==null)
			{
				Response ordDtls = iOrderDetailsMasterDao.getOrderDetailsById(consumptionDto.getOrderDetailsId());
				OrderDetailsMasterDto orderDetailsMaster = (OrderDetailsMasterDto)ordDtls.getObject();
					consumptionDto.setQuantity(orderDetailsMaster.getOrderQty().doubleValue());
				if(orderDetailsMaster.getOrdCancelled()=='Y')
				{
					return new Response(ERROR, ALREADY_EXIST_CODE, "Service is already cancelled by another user...!!", null, null);
				}	
			}
			int i = 0;
			if(consumptionDto.getSaleId()==null)
			{
				if(consumptionDto.getOrderDetailsPayeeMapperDtosList()!=null && consumptionDto.getOrderDetailsPayeeMapperDtosList().size()>0)
					i = iContractConsumptionDao.addCapAmount(consumptionDto);
				
				if(consumptionDto.getOrderDetailsPackageMapperDtosList()!=null && consumptionDto.getOrderDetailsPackageMapperDtosList().size()>0)
					i = iMPackageMasterDao.cancelPackageService(consumptionDto);
			}
			
			if(consumptionDto.getOrderDetailsId()!=null && consumptionDto.getOrderDetailsId()>0)
			{
				CancelOrderDto cancel= new CancelOrderDto();
					cancel.setOrderDetailsId(consumptionDto.getOrderDetailsId());
					cancel.setUserId(consumptionDto.getCreatedBy());
					cancel.setOrdCancelReasonId(consumptionDto.getOrdCancelReasonId());
					cancel.setOrdCancelRemark(consumptionDto.getOrdCancelRemark());
				iOrderDetailsMasterDao.cancelUnRenderedServices(cancel);
			}
			return new Response<>(SUCCESS, SUCCESS_CODE, COMMON_UPDATE, null, null);
		}catch(Exception e){
			
		}
		return new Response<>(ERROR, COMMON_ERROR_CODE, COMMON_ERROR_MESSAGE, null, null);
	}
	
	
	@Override
	@Transactional
	public Response addCapContract(ContractConsumptionDto consumptionDto) throws ApplicationException {
		try{
			int i = 0;
			if(consumptionDto.getOrderDetailsPayeeMapperDtosList()!=null && consumptionDto.getOrderDetailsPayeeMapperDtosList().size()>0)
				i = iContractConsumptionDao.addCapAmount(consumptionDto);
			
			return new Response<>(SUCCESS, SUCCESS_CODE, COMMON_UPDATE, null, null);
		}catch(Exception e){
			
		}
		return new Response<>(ERROR, COMMON_ERROR_CODE, COMMON_ERROR_MESSAGE, null, null);
	}
	
	
	@Override
	@Transactional
	public Response addCapPackage(ContractConsumptionDto consumptionDto) throws ApplicationException {
		try{
			int i = 0;
			
			if(consumptionDto.getOrderDetailsPackageMapperDtosList()!=null && consumptionDto.getOrderDetailsPackageMapperDtosList().size()>0)
				i = iMPackageMasterDao.cancelPackageService(consumptionDto);
			
			
			return new Response<>(SUCCESS, SUCCESS_CODE, COMMON_UPDATE, null, null);
		}catch(Exception e){
			
		}
		return new Response<>(ERROR, COMMON_ERROR_CODE, COMMON_ERROR_MESSAGE, null, null);
	}

	@Transactional
	@Override
	public Response getActivePaymentEntitlement(ContractConsumptionDto dto) throws ApplicationException {
   try{
	   
	       ContractConsumptionDto contractDto = null;
	       Response packageRes =null;
			Response contractRes = iContractConsumptionDao.getActivePaymentEntitlement(dto);
			{
                if(contractRes.getListObject()!=null && contractRes.getListObject().size()>0)
                {
                	contractDto = (ContractConsumptionDto) contractRes.getListObject().get(0);
                	if(contractDto.getPatientId()!=null)
                	{
                		 packageRes = iMPackageMasterService.getExistingActivePackageByPatientEncounter(contractDto.getPatientId(), (contractDto.getVisitTypeId().intValue()==1?contractDto.getEncounterId():contractDto.getAdmissionId()), contractDto.getVisitTypeId(), contractDto.getOrganizationId(),contractDto.getUnitId(),0);
                	}
                }
			}
			return new Response(SUCCESS, SUCCESS_CODE, null, contractRes.getListObject(), (packageRes!=null)?packageRes.getListObject():null);
		}catch(Exception e){
			
		}
		return new Response<>(ERROR, COMMON_ERROR_CODE, COMMON_ERROR_MESSAGE, null, null);
	}

	@Transactional
	@Override
	public Response isConsultationAmountPaidBySelf(ContractConsumptionDto dto) throws ApplicationException {
		try{
			return iContractConsumptionDao.isConsultationAmountPaidBySelf(dto);
		}catch(Exception e){
			
		}
		return new Response<>(ERROR, COMMON_ERROR_CODE, COMMON_ERROR_MESSAGE, null, null);
	}



	@Override
	@Transactional
	public Response consumePackageFromContract(ServiceSearchResDto searchResDto) throws ApplicationException {
		 try{
			 	Integer priorityId = 0;
				Integer count = 0;
				Double rate = null;
				Double selfPayable = 0.00;
				Double creaditPayable = 0.00;
				Double patientClassPer = 0.00;
				Double concessionPer=0.00;
				List<ContractConsumptionDto> serviceDetailslist = null;
				OrderDetailsPayeeMapperDto detailsPayeeMapperDto = null;
				List<OrderDetailsPayeeMapperDto> orderDetailsPayeeMapperDtosList = new LinkedList<>();
				Double netAmt = 0.0;
				Double totalCreditPayable = 0.00;
				Double totalSelfPayable = 0.00;
				Double preAuthAmount = 0.00;
				ContractConsumptionDto finalDto = new ContractConsumptionDto();	
				ContractConsumptionDto patientClassDto = new ContractConsumptionDto();
				//Integer patientClassId= iContractConsumptionDao.getPatientClassByVisit(searchResDto.getVisitTypeId(), searchResDto.getEncounterId(), searchResDto.getAdmissionId());
				Double concession = 0.00;
				Double discount = 0.00;
				ContractConsumptionDto consumptionDto = new ContractConsumptionDto();
				 	consumptionDto.setAdmissionId(searchResDto.getAdmissionId());
				 	consumptionDto.setEncounterId(searchResDto.getEncounterId());
				 	consumptionDto.setContractId(searchResDto.getContractId());
				 	consumptionDto.setServiceId(searchResDto.getServiceMasterId());
				 	consumptionDto.setCurrentDate(searchResDto.getOrderDate()!=null ? searchResDto.getOrderDate() : GlobalCommonDateUtils.getStringDate(TimeZoneComponent.getDateByzone(searchResDto.getUnitId()), "yyyy-MM-dd HH:mm:ss"));
				 	consumptionDto.setOrganizationId(searchResDto.getOrganizationId());
				 	consumptionDto.setUnitId(searchResDto.getUnitId());
				 	consumptionDto.setVisitTypeId(searchResDto.getVisitTypeId());
				 	consumptionDto.setBillingBedCategoryId(searchResDto.getBillingBedCategoryId());
				 	consumptionDto.setPatientCategoryId(searchResDto.getPatientCategoryId());
				 	consumptionDto.setDefaultSelfTariffId(searchResDto.getDefaultSelfTariffId());
				 	consumptionDto.setPackageMasterId(searchResDto.getPackageMasterId());
				 	consumptionDto.setChangedPrice(searchResDto.getChangedPrice());
				 	//consumptionDto.setOrdDiscount(searchResDto.getOrdDiscAmt()!=null ? searchResDto.getOrdDiscAmt() : 0.00);
				 	consumptionDto.setOrdDiscountPer(searchResDto.getOrdDiscPer()!=null ? searchResDto.getOrdDiscPer() : 0.00);
				 	consumptionDto.setPatientClassId(searchResDto.getPatientClassId());
				 	consumptionDto.setUpdateCap(searchResDto.getUpdateCap());
				 	consumptionDto.setPatientClassPercentage(searchResDto.getPatientClassPer());
				if(consumptionDto.getContractId()!=null && consumptionDto.getContractId().intValue()>0)
				{
					serviceDetailslist =  iContractConsumptionDao.getPackageDetailsByContractId(consumptionDto);
					for(ContractConsumptionDto resDto : serviceDetailslist)
					{	
						resDto.setEncounterId(consumptionDto.getEncounterId());
						resDto.setAdmissionId(consumptionDto.getAdmissionId());
						
						
						if(consumptionDto.getPatientClassPercentage()!=null && consumptionDto.isPatientClassApplied()==false)
							patientClassPer = consumptionDto.getPatientClassPercentage();
						else if((resDto.getIsDiscount()=='Y' && resDto.getIsSchemeDiscount()=='Y')||(resDto.getIsSchemeDiscount()=='N'))
						{
							if(searchResDto.getIsFromEncounter()==null)
								 patientClassDto = iContractConsumptionDao.getPatientClassAndPercentageByVisit(consumptionDto);	
							 else
								 patientClassDto = iContractConsumptionDao.getPatientClassAndPercentageByVisitWithountEncounter(consumptionDto);
							
							if(patientClassDto !=null && patientClassDto.getMarkupStatus()!=null && patientClassDto.getMarkupStatus()==false && patientClassDto.getPatientClassPercentage()!=null && patientClassDto.getPatientClassPercentage()>0)
								patientClassPer = patientClassDto.getPatientClassPercentage();
						}else {
							patientClassPer =0.00;
						}
						
						
						if(totalCreditPayable==0)
						{
							priorityId = resDto.getPriorityId();
							if(resDto!=null)
							{
								if(resDto.getType()==1)
								{
									if(patientClassPer >0)
									{
										concession = resDto.getServiceRate()*(patientClassPer*0.01);
										discount = ((resDto.getServiceRate()-concession)*consumptionDto.getOrdDiscountPer()*0.01);
										
										resDto.setFinalPrice(resDto.getServiceRate()-(concession+discount) + (resDto.getServiceRate()-(concession+discount))*(resDto.getTaxPercentage()*0.01));
										resDto.setFinalPrice(new Double(OrderMasterServiceImpl.df2.format(resDto.getFinalPrice())));
										resDto.setSelfPayable(resDto.getFinalPrice()*(resDto.getCoShare()*0.01));
										resDto.setSelfPayable(new Double(OrderMasterServiceImpl.df2.format(resDto.getSelfPayable())));
										resDto.setCreaditPayable(resDto.getFinalPrice()-resDto.getSelfPayable());
									}
									if(consumptionDto.getChangedPrice()!=null && consumptionDto.getChangedPrice()>=0) 
									{
										concession = consumptionDto.getChangedPrice()*(patientClassPer*0.01);
										discount = ((consumptionDto.getChangedPrice()-concession)*consumptionDto.getOrdDiscountPer()*0.01);
										
										resDto.setServiceRate(consumptionDto.getChangedPrice());
										resDto.setFinalPrice((consumptionDto.getChangedPrice()-(concession+discount) + (consumptionDto.getChangedPrice()-(concession+discount))*(resDto.getTaxPercentage()*0.01)));
										resDto.setFinalPrice(new Double(OrderMasterServiceImpl.df2.format(resDto.getFinalPrice())));
										resDto.setSelfPayable(resDto.getFinalPrice()*((resDto.getCoShare()!=null ? resDto.getCoShare() : 0.00 )*0.01));
										resDto.setSelfPayable(new Double(OrderMasterServiceImpl.df2.format(resDto.getSelfPayable())));
										resDto.setCreaditPayable(resDto.getFinalPrice()-resDto.getSelfPayable());
									}
									
									if(resDto.getBalanceCapAmount()==null)
									{
									    selfPayable = resDto.getSelfPayable();
									    creaditPayable = resDto.getCreaditPayable();
									    preAuthAmount = resDto.getPreAuthBalAmt();
									    
									    if(preAuthAmount!=null && searchResDto.isApplyAuth())
										{
											if(creaditPayable>preAuthAmount)
											{
												selfPayable = selfPayable + creaditPayable - preAuthAmount;
												creaditPayable = preAuthAmount;
											}
											if(consumptionDto.getUpdateCap()==null && creaditPayable>0)
												iContractConsumptionDao.updatePreAuthAmount(resDto.getPreauthId(),creaditPayable);
										}
									    
									    totalCreditPayable = totalCreditPayable+creaditPayable;
										totalSelfPayable = totalSelfPayable+selfPayable;
									}
									
									if(resDto.getBalanceCapAmount()!=null)
									{
										if(resDto.getCreaditPayable() < resDto.getBalanceCapAmount())
										{
											selfPayable = resDto.getSelfPayable();
											creaditPayable =  resDto.getCreaditPayable();
										}
										else
										{
											selfPayable = resDto.getSelfPayable()+(resDto.getCreaditPayable()-resDto.getBalanceCapAmount());
											creaditPayable = resDto.getBalanceCapAmount();
										}
										
										if(preAuthAmount!=null && searchResDto.isApplyAuth())
										{
											if(creaditPayable>preAuthAmount)
											{
												selfPayable = selfPayable + creaditPayable - preAuthAmount;
												creaditPayable = preAuthAmount;
											}
											if(consumptionDto.getUpdateCap()==null && creaditPayable>0)
												iContractConsumptionDao.updatePreAuthAmount(resDto.getPreauthId(),creaditPayable);
										}
										
										if(creaditPayable>0)
										{
												consumptionDto.setSpecialityId(resDto.getSpecialityId());
												consumptionDto.setContractId(resDto.getContractId());
											iContractConsumptionDao.updateCapAmount(consumptionDto,creaditPayable); 
										}
										totalCreditPayable = totalCreditPayable+creaditPayable;
										totalSelfPayable = totalSelfPayable+selfPayable;
									}
								}
							}
							if(creaditPayable>0)
							{
								detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
									detailsPayeeMapperDto.setPayeeId(resDto.getPayeeId());
									detailsPayeeMapperDto.setAssociateCompanyId(resDto.getAssociateCompanyId());
									detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(creaditPayable));
									detailsPayeeMapperDto.setContractId(resDto.getContractId());
									detailsPayeeMapperDto.setServiceId(resDto.getServiceId());
									detailsPayeeMapperDto.setPayeeDesc(resDto.getCompanyName());
									detailsPayeeMapperDto.setSpiltBillAmt(new BigDecimal(creaditPayable));
								orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
							}
						}
						
						if(totalSelfPayable>0)
						{
							List<ContractConsumptionDto> serviceDetailslistsecondRoundList= serviceDetailslist;
							for(ContractConsumptionDto secondRound : serviceDetailslistsecondRoundList)
							{
								Double creaditPayableSecondRound = 0.00;
								Double secondRounfCoShare = 0.00;
								if(secondRound.getPriorityId() > priorityId)
								{
									preAuthAmount = secondRound.getPreAuthBalAmt();
									secondRound.setEncounterId(consumptionDto.getEncounterId());
									secondRound.setAdmissionId(consumptionDto.getAdmissionId());
									if(secondRound.getType()==1)	
									{	
										
										consumptionDto.setSpecialityId(secondRound.getSpecialityId());
										if(secondRound.getBalanceCapAmount()==null)
										{
											if(secondRound.getCreaditPayable() > totalSelfPayable)
												creaditPayableSecondRound = totalSelfPayable;
											else
												creaditPayableSecondRound = secondRound.getCreaditPayable();
											
										}
										if(secondRound.getBalanceCapAmount()!=null)
										{
											if(secondRound.getCreaditPayable() >= totalSelfPayable)
											{
												if(totalSelfPayable < secondRound.getBalanceCapAmount())
													creaditPayableSecondRound=totalSelfPayable;
												else
													creaditPayableSecondRound=secondRound.getBalanceCapAmount();
											}
											else if(secondRound.getCreaditPayable() < totalSelfPayable)
											{
												if(secondRound.getCreaditPayable() < secondRound.getBalanceCapAmount())
													creaditPayableSecondRound=secondRound.getCreaditPayable();
												else
													creaditPayableSecondRound=secondRound.getBalanceCapAmount();
											}
										}
										
										totalCreditPayable = totalCreditPayable+creaditPayableSecondRound;
										totalSelfPayable = totalSelfPayable-creaditPayableSecondRound;
										
										if(creaditPayableSecondRound>0)
										{
											if(secondRound.getCoShare()!=null && secondRound.getCoShare()>0)
											{
												secondRounfCoShare  = ((secondRound.getCoShare()*0.01)*creaditPayableSecondRound);
												creaditPayableSecondRound = creaditPayableSecondRound - secondRounfCoShare ;
												totalSelfPayable = totalSelfPayable+secondRounfCoShare;
												totalCreditPayable =  totalCreditPayable-secondRounfCoShare;
											}
											
											if(preAuthAmount!=null && searchResDto.isApplyAuth())
											{
												if(creaditPayableSecondRound>preAuthAmount)
												{
													double diff = creaditPayableSecondRound - preAuthAmount;
													creaditPayableSecondRound = preAuthAmount;
													
													totalSelfPayable = totalSelfPayable+diff;
													totalCreditPayable =  totalCreditPayable-diff;
												}
												if(consumptionDto.getUpdateCap()==null && creaditPayableSecondRound>0)
													iContractConsumptionDao.updatePreAuthAmount(secondRound.getPreauthId(),creaditPayableSecondRound);	
											} 
											
											consumptionDto.setContractId(secondRound.getContractId());
											if(consumptionDto.getUpdateCap()==null && secondRound.getBalanceCapAmount()!=null && creaditPayableSecondRound>0)
												iContractConsumptionDao.updateCapAmount(consumptionDto,creaditPayableSecondRound);
										}
									
									}
									
									if(creaditPayableSecondRound>0)
									{
										detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
											detailsPayeeMapperDto.setPayeeId(secondRound.getPayeeId());
											detailsPayeeMapperDto.setAssociateCompanyId(secondRound.getAssociateCompanyId());
											detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(creaditPayableSecondRound));
											detailsPayeeMapperDto.setContractId(secondRound.getContractId());
											detailsPayeeMapperDto.setPayeeDesc(secondRound.getCompanyName());
											detailsPayeeMapperDto.setServiceId(secondRound.getServiceId());
											detailsPayeeMapperDto.setSpiltBillAmt(new BigDecimal(creaditPayableSecondRound));
										orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
									}
								}	
							count++;
							}
								
						}
						if((totalSelfPayable==0 && totalCreditPayable>0) || serviceDetailslist.size()==count)
						{	
							
							finalDto.setMinRateEditable(resDto.getMinRateEditable());
							finalDto.setMaxRateEditable(resDto.getMaxRateEditable());
							finalDto.setServiceRate(resDto.getServiceRate()!=null && resDto.getServiceRate()>0 ? resDto.getServiceRate() : rate);
							finalDto.setFinalPrice(resDto.getFinalPrice()!=null && resDto.getFinalPrice()>0 ? resDto.getFinalPrice() : netAmt);
							finalDto.setNetAmount(resDto.getFinalPrice()!=null && resDto.getFinalPrice()>0 ? resDto.getFinalPrice() : netAmt);
							finalDto.setOrdTotalAmount(resDto.getServiceRate()!=null && resDto.getServiceRate()>0 ? resDto.getServiceRate() : rate);
							finalDto.setConcession(concession);
							finalDto.setOrdDiscount(discount);
							finalDto.setCreaditPayable(totalCreditPayable);
							finalDto.setSelfPayable(totalSelfPayable);
							finalDto.setPayeeId(resDto.getPayeeId());
							finalDto.setServiceId(resDto.getServiceId());
								detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
									detailsPayeeMapperDto.setPayeeId(resDto.getSelfPayeeId());
									detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(totalSelfPayable));
									detailsPayeeMapperDto.setServiceId(resDto.getServiceId());
								orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
							finalDto.setCoShare(null);
							finalDto.setTaxId(resDto.getTaxId());
							finalDto.setTaxPercentage(resDto.getTaxPercentage());
							finalDto.setContractId(resDto.getContractId());
							finalDto.setPatientClassPercentage(patientClassPer);
							finalDto.setMarkupStatus(resDto.getMarkupStatus());
							finalDto.setIsPackage(true);
							finalDto.setCoShare(resDto.getCoShare());
							finalDto.setBillingBedCategoryId(consumptionDto.getBillingBedCategoryId());
							if(resDto.getTaxPercentage()!=null && resDto.getTaxPercentage()>0)
								finalDto.setTaxAmt(finalDto.getServiceRate()*(resDto.getTaxPercentage()*0.01));
							else 
								finalDto.setTaxAmt(0.00);
							finalDto.setOrderDetailsPayeeMapperDtosList(orderDetailsPayeeMapperDtosList);
							
							
							List<OrderDetailsPackageMapperDto> mpprList = new ArrayList<>();
							OrderDetailsPackageMapperDto detailsPackageMapper = new OrderDetailsPackageMapperDto();
								detailsPackageMapper.setIsPackageService('N');
								detailsPackageMapper.setIsCapService('N');
								detailsPackageMapper.setPackageAmount(BigDecimal.ZERO);
								detailsPackageMapper.setServiceId(searchResDto.getServiceMasterId());
								detailsPackageMapper.setServiceAmount(BigDecimal.valueOf(finalDto.getServiceRate()));
								detailsPackageMapper.setStatus('A');
								mpprList.add(detailsPackageMapper);
							finalDto.setOrderDetailsPackageMapperDtosList(mpprList);
							
							return new Response(SUCCESS,SUCCESS_CODE,null,null,finalDto);
						}
					}
				}
				if(consumptionDto.getContractId()==null || totalCreditPayable==0.00)
				{
					Double taxAmt = null;
					Double taxPer= 0.00;
					Double finalPrice = null;
					Tax tax = iEncounterMasterDao.getTaxPercentageByServiceId(consumptionDto.getServiceId(),consumptionDto.getUnitId());
					taxPer=(tax!=null && tax.getTaxPercentage()!=null) ? tax.getTaxPercentage() : 0.00;
					
					
					if(consumptionDto.getPatientClassPercentage()!=null && consumptionDto.isPatientClassApplied()==false)
						patientClassPer = consumptionDto.getPatientClassPercentage();
					else{ 
						
						if(searchResDto.getIsFromEncounter()==null)
							 patientClassDto = iContractConsumptionDao.getPatientClassAndPercentageByVisit(consumptionDto);	
						 else
							 patientClassDto = iContractConsumptionDao.getPatientClassAndPercentageByVisitWithountEncounter(consumptionDto);
						
						if(patientClassDto !=null && patientClassDto.getMarkupStatus()!=null && patientClassDto.getMarkupStatus()==false && patientClassDto.getPatientClassPercentage()!=null && patientClassDto.getPatientClassPercentage()>0)
							patientClassPer = patientClassDto.getPatientClassPercentage();
						else 
							patientClassPer = 0.00;
					}
					if(consumptionDto.getChangedPrice()!=null && consumptionDto.getChangedPrice()>=0) 
					{
							concession =consumptionDto.getChangedPrice()*(patientClassPer*0.01);
							discount = ((consumptionDto.getChangedPrice()-concession)*consumptionDto.getOrdDiscountPer()*0.01);
							
							finalDto.setServiceRate(consumptionDto.getChangedPrice());
							taxAmt = (consumptionDto.getChangedPrice()-(concession+discount))*(taxPer*0.01);
							finalDto.setFinalPrice((consumptionDto.getChangedPrice()-(concession+discount) + (consumptionDto.getChangedPrice()-(concession+discount))*(taxPer*0.01)));
							finalDto.setSelfPayable(finalDto.getFinalPrice());
					}else {
						if(patientClassPer > 0.00)
							concession = searchResDto.getBasePrice().doubleValue()*(patientClassPer*0.01);
						else
							concession = 0.00;
						if(tax!=null)
						{
							taxAmt = ((searchResDto.getBasePrice().doubleValue()-concession) * (taxPer / 100));
							finalPrice = searchResDto.getBasePrice().doubleValue()-concession + taxAmt;
							
							finalDto.setTaxId(tax.getId());
							finalDto.setTaxPercentage(tax.getTaxPercentage());
						}else {
							taxAmt = 0.00;
							finalPrice = searchResDto.getBasePrice().doubleValue() - concession;
							finalDto.setTaxPercentage(0.00);
						}
						finalDto.setFinalPrice(finalPrice);
						finalDto.setSelfPayable(finalPrice);
					}
					finalDto.setTaxPercentage(taxPer);
					finalDto.setTaxId(tax!=null ? tax.getId() : null);
					finalDto.setTaxAmt(taxAmt!=null ? taxAmt : 0);
					finalDto.setMinRateEditable(searchResDto.getMinRateEditable()!=null?searchResDto.getMinRateEditable().doubleValue():0.0);
					finalDto.setMaxRateEditable(searchResDto.getMaxRateEditable()!=null?searchResDto.getMaxRateEditable().doubleValue():0.0);
					finalDto.setServiceRate(searchResDto.getBasePrice().doubleValue());
					finalDto.setOrdTotalAmount(searchResDto.getBasePrice().doubleValue());
					finalDto.setPatientClassPercentage(patientClassPer);
					finalDto.setConcession(concession);
					finalDto.setOrdDiscount(discount);
					finalDto.setCreaditPayable(0.00);
					finalDto.setServiceId(consumptionDto.getServiceId());
					finalDto.setPayeeId(searchResDto.getSelfPayeeId());
					finalDto.setNetAmount(finalDto.getFinalPrice());
					finalDto.setIsPackage(true);
						detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
							detailsPayeeMapperDto.setPayeeId(searchResDto.getSelfPayeeId());
							detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(finalDto.getFinalPrice()));
							detailsPayeeMapperDto.setPayeeDesc("Self");
							detailsPayeeMapperDto.setServiceId(consumptionDto.getServiceId());
						orderDetailsPayeeMapperDtosList.add(detailsPayeeMapperDto);
					finalDto.setPatientClassPercentage(consumptionDto.getPatientClassPercentage()!=null ? consumptionDto.getPatientClassPercentage():0.00);
					finalDto.setMarkupStatus(consumptionDto.getMarkupStatus());
					finalDto.setOrderDetailsPayeeMapperDtosList(orderDetailsPayeeMapperDtosList);
					finalDto.setBillingBedCategoryId(consumptionDto.getBillingBedCategoryId());
					finalDto.setCoShare(100.00);
					//Package
					
					List<OrderDetailsPackageMapperDto> mpprList = new ArrayList<>();
					OrderDetailsPackageMapperDto detailsPackageMapper = new OrderDetailsPackageMapperDto();
						detailsPackageMapper.setIsPackageService('N');
						detailsPackageMapper.setIsCapService('N');
						detailsPackageMapper.setPackageAmount(BigDecimal.ZERO);
						detailsPackageMapper.setServiceId(searchResDto.getServiceMasterId());
						detailsPackageMapper.setServiceAmount(BigDecimal.valueOf(searchResDto.getBasePrice().doubleValue()));
						detailsPackageMapper.setStatus('A');
					mpprList.add(detailsPackageMapper);
					finalDto.setOrderDetailsPackageMapperDtosList(mpprList);
					return new Response(SUCCESS,SUCCESS_CODE,null,null,finalDto);
				}
			}catch(Exception e){
				
			}
			return new Response<>(ERROR, COMMON_ERROR_CODE, COMMON_ERROR_MESSAGE, null, null);
	}

	
	@Override
	@Transactional
	public Response getSerivceRateGlobal(ContractConsumptionDto consumptionDto, boolean isProcedure, boolean isFromUpdatePayee)throws ApplicationException 
	{
		try{
			Response res = null;
		    Double consulServiceRate = null;
		    
		    consumptionDto.setUpdateCap('N');
				if(consumptionDto.getIsConsultationService()!=null && consumptionDto.getIsConsultationService().equals('Y') && consumptionDto.getConsultationAmount()==null)
				{
					EncounterMasterDto encounterMasterDto= iEncounterMasterDao.getEncounterDetailsForUpdatePackage(consumptionDto.getEncounterId());
						encounterMasterDto.setPaymentEntitlementId(isFromUpdatePayee == true ? consumptionDto.getPaymentEntitlementId() : encounterMasterDto.getPaymentEntitlementId()); 
					DoctorConsultationServiceMapperDto dto = iDoctorConsultationServiceMapperDao.getDoctorConsultationServiceBySpecialityAndDoctorIdForUpdatePackage(encounterMasterDto);
					if(encounterMasterDto.getUserDefinedVisitTypeId()==1)
						consulServiceRate = dto.getNewVisitRate();
					if(encounterMasterDto.getUserDefinedVisitTypeId()==2)
						consulServiceRate = dto.getFollowupVisitRate();
					if(encounterMasterDto.getUserDefinedVisitTypeId()==3)
						consulServiceRate = dto.getSecondaryVisitRate();
					
					consumptionDto.setConsultationAmount(consulServiceRate);
				}
			
				res= this.consumeServiceFromContract(consumptionDto);
				
				ContractConsumptionDto  contractConsumption = (ContractConsumptionDto)res.getObject();
			
				List<OrderDetailsPayeeMapperDto> orderDetailsPayeeMapperDtoList = new ArrayList<OrderDetailsPayeeMapperDto>();
				OrderDetailsPayeeMapperDto	detailsPayeeMapperDto = new OrderDetailsPayeeMapperDto();
					detailsPayeeMapperDto.setPayeeId(contractConsumption.getSelfPayeeId()!=null ? contractConsumption.getSelfPayeeId() : contractConsumption.getPayeeId());
					detailsPayeeMapperDto.setPayeeAmount(new BigDecimal(contractConsumption.getFinalPrice()));
					detailsPayeeMapperDto.setPayeeDesc("Self");
					detailsPayeeMapperDto.setServiceId(contractConsumption.getServiceId());
					detailsPayeeMapperDto.setIsRateEditable('N');
					detailsPayeeMapperDto.setCoShare(0.00);
				orderDetailsPayeeMapperDtoList.add(detailsPayeeMapperDto);
				contractConsumption.setOrderDetailsPayeeMapperDtosList(orderDetailsPayeeMapperDtoList);
				
				List<OrderDetailsPackageMapperDto> mpprList = new ArrayList<OrderDetailsPackageMapperDto>();
						OrderDetailsPackageMapperDto detailsPackageMapper = new OrderDetailsPackageMapperDto();
						detailsPackageMapper.setIsPackageService('N');
						detailsPackageMapper.setIsCapService('N');
						detailsPackageMapper.setPackageAmount(BigDecimal.ZERO);
						detailsPackageMapper.setServiceId(consumptionDto.getServiceId());
						detailsPackageMapper.setServiceAmount(BigDecimal.valueOf(contractConsumption.getServiceRate()));
						detailsPackageMapper.setOrganizationId(consumptionDto.getOrganizationId());
						detailsPackageMapper.setUnitId(consumptionDto.getUnitId());
						detailsPackageMapper.setStatus('A');
						mpprList.add(detailsPackageMapper);
				contractConsumption.setOrderDetailsPackageMapperDtosList(mpprList);
					
				return new Response(SUCCESS,SUCCESS_CODE,"",res.getListObject(),contractConsumption);
				
						
		}catch(Exception e){
			throw e;
		 }
	}

	@Override
	@Transactional
	public OrderMasterDto getPatientClassByVisit(OrderMasterDto orderMasterDto) throws ApplicationException {
		try{
				return iContractConsumptionDao.getPatientClassByVisit(orderMasterDto);
		}catch(Exception e){
			throw e;
		 }
	}
	@Override
	@Transactional
	public Response getSerivceRateGlobalAfterTariffChange(ContractConsumptionDto consumptionDto, boolean isProcedure, boolean isFromUpdatePayee)throws ApplicationException 
	{
		try{
			Response res = null;
				res= this.consumeServiceFromContractAfterTariffChange(consumptionDto);
				return new Response(SUCCESS,SUCCESS_CODE,"",null,res.getObject());
				
						
		}catch(Exception e){
			throw e;
		 }
	}
	public Response consumeServiceFromContractAfterTariffChange(ContractConsumptionDto consumptionDto) {
		try{
			List<ContractConsumptionDto> serviceDetailslist = null;
			ContractConsumptionDto finalDto = new ContractConsumptionDto();
				serviceDetailslist =  iContractConsumptionDao.getServiceDetailsByContractIdAfterTariffChange(consumptionDto);
				for(ContractConsumptionDto resDto : serviceDetailslist)
				{	
					finalDto.setServiceRate(resDto.getServiceRate());
					
				}
				return new Response(SUCCESS,SUCCESS_CODE,"Service is not mapped with the tariff..!!",null,finalDto);
		}catch(Exception e){
			
		}
		return new Response<>(ERROR, COMMON_ERROR_CODE, COMMON_ERROR_MESSAGE, null, null);
	}

	@Override
	@Transactional
	public Character getIsBedChargesService(ContractConsumptionDto contractResp) {
		return iEncounterMasterDao.getIsBedChargesService(contractResp);
	}

	@Override
	@Transactional
	public boolean getGstApplicableForBillingBedCategory(Integer unitId) {
		return iContractConsumptionDao.getGstApplicableForBillingBedCategory(unitId);
	}

	@Override
	@Transactional
	public List<ContractConsumptionDto> getgstDetails(Integer billingBedCategoryId) {
		return iContractConsumptionDao.getgstDetails(billingBedCategoryId);
	}
	
	
}
