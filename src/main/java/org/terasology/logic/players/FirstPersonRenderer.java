/*
 * Copyright 2013 Moving Blocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.logic.players;

import com.google.common.collect.Maps;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.asset.AssetType;
import org.terasology.asset.AssetUri;
import org.terasology.asset.Assets;
import org.terasology.engine.CoreRegistry;
import org.terasology.entitySystem.EntityRef;
import org.terasology.entitySystem.RegisterMode;
import org.terasology.entitySystem.systems.In;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.RenderSystem;
import org.terasology.logic.characters.CharacterComponent;
import org.terasology.logic.characters.CharacterMovementComponent;
import org.terasology.logic.inventory.ItemComponent;
import org.terasology.logic.inventory.SlotBasedInventoryManager;
import org.terasology.logic.manager.GUIManager;
import org.terasology.logic.manager.ShaderManager;
import org.terasology.math.TeraMath;
import org.terasology.rendering.assets.mesh.Mesh;
import org.terasology.rendering.assets.texture.Texture;
import org.terasology.rendering.gui.widgets.UIInventoryGrid;
import org.terasology.rendering.icons.Icon;
import org.terasology.rendering.primitives.MeshFactory;
import org.terasology.rendering.primitives.Tessellator;
import org.terasology.rendering.primitives.TessellatorHelper;
import org.terasology.rendering.shader.ShaderProgram;
import org.terasology.rendering.world.WorldRenderer;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockPart;
import org.terasology.world.block.family.BlockFamily;
import org.terasology.world.block.items.BlockItemComponent;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_GREATER;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glAlphaFunc;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glScalef;
import static org.lwjgl.opengl.GL11.glTranslatef;

/**
 * @author Immortius <immortius@gmail.com>
 */
@RegisterSystem(RegisterMode.CLIENT)
public class FirstPersonRenderer implements RenderSystem {
    private static final Logger logger = LoggerFactory.getLogger(FirstPersonRenderer.class);

    @In
    private WorldProvider worldProvider;
    @In
    private LocalPlayer localPlayer;
    @In
    private WorldRenderer worldRenderer;
    @In
    private SlotBasedInventoryManager inventoryManager;

    private Mesh handMesh;
    private Texture handTex;

    private Map<String, Mesh> iconMeshes = Maps.newHashMap();

    @Override
    public void initialise() {
        Vector2f texPos = new Vector2f(40.0f * 0.015625f, 32.0f * 0.03125f);
        Vector2f texWidth = new Vector2f(4.0f * 0.015625f, -12.0f * 0.03125f);

        Tessellator tessellator = new Tessellator();
        TessellatorHelper.addBlockMesh(tessellator, new Vector4f(1, 1, 1, 1), texPos, texWidth, 1.0f, 1.0f, 0.9f, 0.0f, 0.0f, 0.0f);
        handMesh = tessellator.generateMesh();
        handTex = Assets.getTexture("engine:char");
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void renderOpaque() {

    }

    @Override
    public void renderTransparent() {

    }

    @Override
    public void renderFirstPerson() {
        CharacterComponent character = localPlayer.getCharacterEntity().getComponent(CharacterComponent.class);
        if (character == null) {
            return;
        }
        CharacterMovementComponent charMoveComp = localPlayer.getCharacterEntity().getComponent(CharacterMovementComponent.class);
        if (charMoveComp == null) {
            return;
        }
        float bobOffset = calcBobbingOffset(charMoveComp.footstepDelta, (float) java.lang.Math.PI / 8f, 0.05f);
        float handMovementAnimationOffset = character.handAnimation;

        UIInventoryGrid toolbar = (UIInventoryGrid) CoreRegistry.get(GUIManager.class).getWindowById("hud").getElementById("toolbar");
        int invSlotIndex = character.selectedTool + toolbar.getStartSlot();
        EntityRef heldItem = inventoryManager.getItemInSlot(localPlayer.getCharacterEntity(), invSlotIndex);
        ItemComponent heldItemComp = heldItem.getComponent(ItemComponent.class);
        BlockItemComponent blockItem = heldItem.getComponent(BlockItemComponent.class);
        if (blockItem != null && blockItem.blockFamily != null) {
            renderBlock(blockItem.blockFamily, bobOffset, handMovementAnimationOffset);
        } else if (heldItemComp != null && heldItemComp.renderWithIcon) {
            renderIcon(heldItemComp.icon, bobOffset, handMovementAnimationOffset);
        } else {
            renderHand(bobOffset, handMovementAnimationOffset);
        }

    }

    @Override
    public void renderOverlay() {

    }

    private void renderHand(float bobOffset, float handMovementAnimationOffset) {
        ShaderProgram shader = ShaderManager.getInstance().getShaderProgram("block");
        shader.enable();
        shader.setFloat("light", worldRenderer.getRenderingLightValue());
        glBindTexture(GL11.GL_TEXTURE_2D, handTex.getId());

        glPushMatrix();
        glTranslatef(0.8f, -0.8f + bobOffset - handMovementAnimationOffset * 0.5f, -1.0f - handMovementAnimationOffset * 0.5f);
        glRotatef(-45f - handMovementAnimationOffset * 64.0f, 1.0f, 0.0f, 0.0f);
        glRotatef(35f, 0.0f, 1.0f, 0.0f);
        glTranslatef(0f, 0.25f, 0f);
        glScalef(0.3f, 0.6f, 0.3f);

        handMesh.render();

        glPopMatrix();
    }

    private void renderIcon(String iconName, float bobOffset, float handMovementAnimationOffset) {
        ShaderProgram shader = ShaderManager.getInstance().getShaderProgram("block");
        shader.enable();

        shader.setInt("textured", 0);
        shader.setFloat("light", worldRenderer.getRenderingLightValue());

        glPushMatrix();

        glTranslatef(1.0f, -0.7f + bobOffset - handMovementAnimationOffset * 0.5f, -1.5f - handMovementAnimationOffset * 0.5f);
        glRotatef(-handMovementAnimationOffset * 64.0f, 1.0f, 0.0f, 0.0f);
        glRotatef(-20f, 1.0f, 0.0f, 0.0f);
        glRotatef(-80f, 0.0f, 1.0f, 0.0f);
        glRotatef(45f, 0.0f, 0.0f, 1.0f);

        Mesh itemMesh = iconMeshes.get(iconName);
        if (itemMesh == null) {
            Icon icon = Icon.get(iconName);
            itemMesh = MeshFactory.generateItemMesh(new AssetUri(AssetType.MESH, "engine", "icon." + iconName), icon.getX(), icon.getY());
            iconMeshes.put(iconName, itemMesh);
        }

        itemMesh.render();

        glPopMatrix();
    }

    private void renderBlock(BlockFamily blockFamily, float bobOffset, float handMovementAnimationOffset) {
        Block activeBlock = blockFamily.getArchetypeBlock();
        Vector3f playerPos = localPlayer.getPosition();

        // Adjust the brightness of the block according to the current position of the player
        ShaderProgram shader = ShaderManager.getInstance().getShaderProgram("block");
        shader.enable();

        // Apply biome and overall color offset
        Vector4f color = activeBlock.calcColorOffsetFor(BlockPart.CENTER, worldProvider.getBiomeProvider().getTemperatureAt(TeraMath.floorToInt(playerPos.x), TeraMath.floorToInt(playerPos.z)), worldProvider.getBiomeProvider().getHumidityAt(TeraMath.floorToInt(playerPos.x), TeraMath.floorToInt(playerPos.z)));
        shader.setFloat3("colorOffset", color.x, color.y, color.z);

        glEnable(GL11.GL_BLEND);

        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glAlphaFunc(GL_GREATER, 0.1f);
        if (activeBlock.isTranslucent()) {
            glEnable(GL11.GL_ALPHA_TEST);
        }

        glPushMatrix();

        glTranslatef(1.0f, -0.7f + bobOffset - handMovementAnimationOffset * 0.5f, -1.5f - handMovementAnimationOffset * 0.5f);
        glRotatef(-25f - handMovementAnimationOffset * 64.0f, 1.0f, 0.0f, 0.0f);
        glRotatef(35f, 0.0f, 1.0f, 0.0f);
        glTranslatef(0f, 0.25f, 0f);

        activeBlock.renderWithLightValue(worldRenderer.getRenderingLightValue());

        glPopMatrix();

        if (activeBlock.isTranslucent()) {
            glDisable(GL11.GL_ALPHA_TEST);
        }
        glDisable(GL11.GL_BLEND);
    }

    private float calcBobbingOffset(float counter, float phaseOffset, float amplitude) {
        return (float) java.lang.Math.sin(2 * Math.PI * counter + phaseOffset) * amplitude;
    }


}