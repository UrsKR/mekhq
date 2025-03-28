/*
 * Copyright (c) 2019 The MegaMek Team
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MekHQ. If not, see <http://www.gnu.org/licenses/>.
 */
package mekhq.campaign.parts;

import megamek.common.Entity;
import megamek.common.EquipmentType;
import megamek.common.ITechnology;
import megamek.common.TechAdvancement;
import mekhq.utilities.MHQXMLUtility;
import mekhq.campaign.Campaign;
import mekhq.campaign.finances.Money;
import mekhq.campaign.work.IAcquisitionWork;
import org.apache.logging.log4j.LogManager;
import org.w3c.dom.Node;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Standard support vehicle armor, which can differ by BAR and tech rating.
 */
public class SVArmor extends Armor {
    private int bar;
    private int techRating;

    /**
     * Constructor used during campaign deserialization
     */
    @SuppressWarnings("unused")
    public SVArmor() {
        this(2, RATING_D, 0, Entity.LOC_NONE, null);
    }

    /**
     * Create an instance of a support vehicle armor part
     *
     * @param bar          The Barrier Armor Rating for the armor
     * @param techRating   The armor tech rating
     * @param points       The number of points of armor
     * @param loc          The location on the unit
     * @param campaign     The campaign instance
     */
    public SVArmor(int bar, int techRating, int points, int loc, Campaign campaign) {
        super(0, EquipmentType.T_ARMOR_STANDARD, points, loc, false, false, campaign);
        this.bar = bar;
        this.techRating = techRating;
        this.name = String.format("BAR %d armor (%s)", bar, ITechnology.getRatingName(techRating));
    }

    public int getBAR() {
        return bar;
    }

    @Override
    public int getTechRating() {
        return techRating;
    }

    @Override
    public SVArmor clone() {
        SVArmor clone = new SVArmor(getBAR(), getTechRating(), getAmount(), getLocation(), campaign);
        clone.copyBaseData(this);
        return clone;
    }

    @Override
    public double getTonnage() {
        return amount * EquipmentType.getSupportVehicleArmorWeightPerPoint(bar, techRating);
    }

    @Override
    public Money getActualValue() {
        return adjustCostsForCampaignOptions(
                Money.of(amount * EquipmentType.getSupportVehicleArmorCostPerPoint(bar)));
    }

    @Override
    public double getTonnageNeeded() {
        return amountNeeded * EquipmentType.getSupportVehicleArmorWeightPerPoint(bar, techRating);
    }

    @Override
    public Money getValueNeeded() {
        return adjustCostsForCampaignOptions(
                Money.of(amountNeeded * EquipmentType.getSupportVehicleArmorCostPerPoint(bar)));
    }

    @Override
    public Money getStickerPrice() {
        // always in 5-ton increments
        return Money.of(5.0 / EquipmentType.getSupportVehicleArmorWeightPerPoint(bar, techRating)
                * EquipmentType.getSupportVehicleArmorCostPerPoint(bar));
    }

    @Override
    public boolean isSamePartType(Part part) {
        return (getClass() == part.getClass())
                && (bar == ((SVArmor) part).bar)
                && (techRating == ((SVArmor) part).techRating);
    }

    @Override
    public double getArmorWeight(int points) {
        return points * EquipmentType.getSupportVehicleArmorWeightPerPoint(bar, techRating);
    }

    @Override
    public IAcquisitionWork getAcquisitionWork() {
        return new SVArmor(bar, techRating, (int) Math.round(5.0 / getArmorPointsPerTon()), -1, campaign);
    }

    @Override
    public double getArmorPointsPerTon() {
        return 1.0 / EquipmentType.getSupportVehicleArmorWeightPerPoint(bar, techRating);
    }

    @Override
    public Part getNewPart() {
        return new SVArmor(bar, techRating, (int) Math.round(5 * getArmorPointsPerTon()), -1, campaign);
    }

    @Override
    public int getAmountAvailable() {
        SVArmor a = (SVArmor) campaign.getWarehouse().findSparePart(part -> {
            return isSamePartType(part)
                && part.isPresent()
                && !part.isReservedForRefit();
        });

        return a != null ? a.getAmount() : 0;
    }

    @Override
    public void changeAmountAvailable(int amount) {
        SVArmor a = (SVArmor) campaign.getWarehouse().findSparePart(part -> {
            return isSamePartType(part)
                && part.isPresent()
                && Objects.equals(getRefitUnit(), part.getRefitUnit());
        });

        if (null != a) {
            a.setAmount(a.getAmount() + amount);
            if (a.getAmount() <= 0) {
                campaign.getWarehouse().removePart(a);
            }
        } else if (amount > 0) {
            campaign.getQuartermaster().addPart(new SVArmor(bar, techRating, amount, -1, campaign), 0);
        }
    }

    @Override
    protected void writeToXMLEnd(final PrintWriter pw, int indent) {
        MHQXMLUtility.writeSimpleXMLTag(pw, indent, "bar", bar);
        MHQXMLUtility.writeSimpleXMLTag(pw, indent, "techRating", ITechnology.getRatingName(techRating));
        super.writeToXMLEnd(pw, indent);
    }

    @Override
    protected void loadFieldsFromXmlNode(Node node) {
        super.loadFieldsFromXmlNode(node);
        for (int x = 0; x < node.getChildNodes().getLength(); x++) {
            final Node wn = node.getChildNodes().item(x);
            try {
                switch (wn.getNodeName()) {
                    case "bar":
                        bar = Integer.parseInt(wn.getTextContent());
                        break;
                    case "techRating":
                        for (int r = 0; r < ratingNames.length; r++) {
                            if (ratingNames[r].equals(wn.getTextContent())) {
                                techRating = r;
                                break;
                            }
                        }
                        break;
                }
            } catch (Exception e) {
                LogManager.getLogger().error("", e);
            }
        }
    }

    @Override
    public TechAdvancement getTechAdvancement() {
        return EquipmentType.getSVArmorTechAdvancement(bar);
    }
}
