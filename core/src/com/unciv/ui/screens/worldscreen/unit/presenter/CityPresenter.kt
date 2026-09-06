package com.unciv.ui.screens.worldscreen.unit.presenter

import com.unciv.logic.city.City
import com.unciv.logic.map.HexCoord
import com.unciv.models.translations.tr
import com.unciv.ui.components.input.onClick
import com.unciv.ui.screens.pickerscreens.CityRenamePopup
import com.unciv.ui.screens.worldscreen.unit.UnitTable
import com.unciv.view.ForeignCityView

class CityPresenter(private val unitTable: UnitTable, private val unitPresenter: UnitPresenter) : UnitTable.Presenter {

    var selectedCity: ForeignCityView? = null

    override val position: HexCoord?
        get() = selectedCity?.location

    fun selectCity(city: City): Boolean {
        // If the last selected unit connecting a road, keep it selected. Otherwise, clear.
        unitPresenter.apply {
            if (selectedUnitIsConnectingRoad) {
                selectUnit(selectedUnits[0])
                selectedUnitIsConnectingRoad = true // selectUnit resets this
            } else {
                selectUnit()
            }
        }
        if (city === selectedCity?.getCity()) return false
        selectedCity = unitTable.worldScreen.selectedGameView.getForeignCityView(city)
        return true
    }

    override fun updateWhenNeeded() = with(unitTable) {
        separator.isVisible = true
        val city = selectedCity!!
        var nameLabelText = city.name.tr()
        if (city.getHealth() < city.getMaxHealth()) nameLabelText += " (${city.getHealth().tr()})"

        if (!unitNameLabel.text.equalsString(nameLabelText)) {
            unitNameLabel.setText(nameLabelText)
            unitNameLabel.clearListeners()
            unitNameLabel.onClick {
                if (!worldScreen.canChangeState) return@onClick
                CityRenamePopup(
                    screen = worldScreen,
                    cityView = worldScreen.selectedGameView.getCityView(city.getCity()),
                    actionOnClose = {
                        unitNameLabel.setText(city.name.tr())
                        worldScreen.shouldUpdate = true
                    })
            }
        }

        descriptionTable.clear()
        descriptionTable.defaults().pad(2f).padRight(5f)
        val ownCity = if (worldScreen.portraitLayout) city.tryGetCityView()?.getCity() else null
        if (ownCity != null) {
            // Phone: what a player manages (size and what is being built) rather than combat strength
            descriptionTable.add("Population".tr())
            descriptionTable.add(ownCity.population.population.tr()).row()
            val construction = ownCity.cityConstructions.getCurrentConstruction()
            val turns = ownCity.cityConstructions.turnsToConstruction(construction.name)
            descriptionTable.add("Current construction".tr())
            descriptionTable.add(construction.name.tr() + if (construction is com.unciv.models.ruleset.PerpetualConstruction) "" else " (${turns.tr()}${com.unciv.ui.components.fonts.Fonts.turn})").row()
        } else {
            descriptionTable.add("Strength".tr())
            descriptionTable.add(city.getDefendingStrength().tr()).row()
            descriptionTable.add("Bombard strength".tr())
            descriptionTable.add(city.getAttackingStrength().tr()).row()
        }

        shouldUpdate = true

    }
}
