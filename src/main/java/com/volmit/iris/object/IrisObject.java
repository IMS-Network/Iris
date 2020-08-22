package com.volmit.iris.object;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.util.BlockVector;

import com.volmit.iris.util.B;
import com.volmit.iris.util.BlockPosition;
import com.volmit.iris.util.ChunkPosition;
import com.volmit.iris.util.IObjectPlacer;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisObject extends IrisRegistrant
{
	private static final Material SNOW = Material.SNOW;
	private static final BlockData AIR = B.getBlockData("CAVE_AIR");
	private static final BlockData[] SNOW_LAYERS = new BlockData[] {B.getBlockData("minecraft:snow[layers=1]"), B.getBlockData("minecraft:snow[layers=2]"), B.getBlockData("minecraft:snow[layers=3]"), B.getBlockData("minecraft:snow[layers=4]"), B.getBlockData("minecraft:snow[layers=5]"), B.getBlockData("minecraft:snow[layers=6]"), B.getBlockData("minecraft:snow[layers=7]"), B.getBlockData("minecraft:snow[layers=8]")};
	private KMap<BlockVector, BlockData> blocks;
	private int w;
	private int d;
	private int h;
	private transient BlockVector center;

	public IrisObject(int w, int h, int d)
	{
		blocks = new KMap<>();
		this.w = w;
		this.h = h;
		this.d = d;
		center = new BlockVector(w / 2, h / 2, d / 2);
	}

	public static BlockVector sampleSize(File file) throws IOException
	{
		FileInputStream in = new FileInputStream(file);
		DataInputStream din = new DataInputStream(in);
		BlockVector bv = new BlockVector(din.readInt(), din.readInt(), din.readInt());
		din.close();
		return bv;
	}

	public void read(InputStream in) throws IOException
	{
		DataInputStream din = new DataInputStream(in);
		this.w = din.readInt();
		this.h = din.readInt();
		this.d = din.readInt();
		center = new BlockVector(w / 2, h / 2, d / 2);
		int s = din.readInt();

		for(int i = 0; i < s; i++)
		{
			blocks.put(new BlockVector(din.readShort(), din.readShort(), din.readShort()), B.getBlockData(din.readUTF()));
		}
	}

	public void read(File file) throws IOException
	{
		FileInputStream fin = new FileInputStream(file);
		read(fin);
		fin.close();
	}

	public void write(File file) throws IOException
	{
		file.getParentFile().mkdirs();
		FileOutputStream out = new FileOutputStream(file);
		write(out);
		out.close();
	}

	public void write(OutputStream o) throws IOException
	{
		DataOutputStream dos = new DataOutputStream(o);
		dos.writeInt(w);
		dos.writeInt(h);
		dos.writeInt(d);
		dos.writeInt(blocks.size());
		for(BlockVector i : blocks.k())
		{
			dos.writeShort(i.getBlockX());
			dos.writeShort(i.getBlockY());
			dos.writeShort(i.getBlockZ());
			dos.writeUTF(blocks.get(i).getAsString(true));
		}
	}

	public void setUnsigned(int x, int y, int z, BlockData block)
	{
		if(x >= w || y >= h || z >= d)
		{
			throw new RuntimeException(x + " " + y + " " + z + " exceeds limit of " + w + " " + h + " " + d);
		}

		BlockVector v = new BlockVector(x, y, z).subtract(center).toBlockVector();

		if(block == null)
		{
			blocks.remove(v);
		}

		else
		{
			blocks.put(v, block);
		}
	}

	public void place(int x, int z, IObjectPlacer placer, IrisObjectPlacement config, RNG rng)
	{
		place(x, -1, z, placer, config, rng);
	}

	public int place(int x, int yv, int z, IObjectPlacer placer, IrisObjectPlacement config, RNG rng)
	{
		return place(x, yv, z, placer, config, rng, null);
	}

	public int place(int x, int yv, int z, IObjectPlacer placer, IrisObjectPlacement config, RNG rng, Consumer<BlockPosition> listener)
	{
		int spinx = rng.imax() / 1000;
		int spiny = rng.imax() / 1000;
		int spinz = rng.imax() / 1000;
		int rty = config.getRotation().rotate(new BlockVector(0, getCenter().getBlockY(), 0), spinx, spiny, spinz).getBlockY();
		int ty = config.getTranslate().translate(new BlockVector(0, getCenter().getBlockY(), 0), config.getRotation(), spinx, spiny, spinz).getBlockY();
		int y = -1;

		if(yv < 0)
		{
			if(config.getMode().equals(ObjectPlaceMode.CENTER_HEIGHT_RIGID))
			{
				if(config.isTranslateCenter())
				{
					y = placer.getHighest(x, z, config.isUnderwater()) + rty;
				}

				else
				{
					y = placer.getHighest(x, z, config.isUnderwater()) + rty;
				}
			}

			if(config.getMode().equals(ObjectPlaceMode.MAX_HEIGHT_RIGID_ACCURATE))
			{
				BlockVector offset = new BlockVector(config.getTranslate().getX(), config.getTranslate().getY(), config.getTranslate().getZ());
				BlockVector rotatedDimensions = config.getRotation().rotate(new BlockVector(getW(), getH(), getD()), spinx, spiny, spinz).clone();

				for(int i = x - (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i <= x + (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i++)
				{
					for(int j = z - (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j <= z + (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j++)
					{
						int h = placer.getHighest(i, j, config.isUnderwater()) + rty;

						if(h > y)
						{
							y = h;
						}
					}
				}
			}

			else if(config.getMode().equals(ObjectPlaceMode.MAX_HEIGHT_RIGID))
			{
				BlockVector offset = new BlockVector(config.getTranslate().getX(), config.getTranslate().getY(), config.getTranslate().getZ());
				BlockVector rotatedDimensions = config.getRotation().rotate(new BlockVector(getW(), getH(), getD()), spinx, spiny, spinz).clone();

				for(int i = x - (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i <= x + (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i += (rotatedDimensions.getBlockX() / 2))
				{
					for(int j = z - (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j <= z + (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j += (rotatedDimensions.getBlockZ() / 2))
					{
						int h = placer.getHighest(i, j, config.isUnderwater()) + rty;

						if(h > y)
						{
							y = h;
						}
					}
				}
			}

			else if(config.getMode().equals(ObjectPlaceMode.MIN_HEIGHT_RIGID_ACCURATE))
			{
				y = 257;
				BlockVector offset = new BlockVector(config.getTranslate().getX(), config.getTranslate().getY(), config.getTranslate().getZ());
				BlockVector rotatedDimensions = config.getRotation().rotate(new BlockVector(getW(), getH(), getD()), spinx, spiny, spinz).clone();

				for(int i = x - (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i <= x + (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i++)
				{
					for(int j = z - (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j <= z + (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j++)
					{
						int h = placer.getHighest(i, j, config.isUnderwater()) + rty;

						if(h < y)
						{
							y = h;
						}
					}
				}
			}

			else if(config.getMode().equals(ObjectPlaceMode.MIN_HEIGHT_RIGID))
			{
				y = 257;
				BlockVector offset = new BlockVector(config.getTranslate().getX(), config.getTranslate().getY(), config.getTranslate().getZ());
				BlockVector rotatedDimensions = config.getRotation().rotate(new BlockVector(getW(), getH(), getD()), spinx, spiny, spinz).clone();

				for(int i = x - (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i <= x + (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i += (rotatedDimensions.getBlockX() / 2))
				{
					for(int j = z - (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j <= z + (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j += (rotatedDimensions.getBlockZ() / 2))
					{
						int h = placer.getHighest(i, j, config.isUnderwater()) + rty;

						if(h < y)
						{
							y = h;
						}
					}
				}
			}

			else if(config.getMode().equals(ObjectPlaceMode.PAINT))
			{
				y = placer.getHighest(x, z, config.isUnderwater()) + rty;
			}
		}

		else
		{
			y = yv;
		}

		if(yv >= 0 && config.isBottom())
		{
			y += Math.floorDiv(h, 2);
		}

		KMap<ChunkPosition, Integer> heightmap = config.getSnow() > 0 ? new KMap<>() : null;

		if(yv < 0)
		{
			if(!config.isUnderwater() && !config.isOnwater() && placer.isUnderwater(x, z))
			{
				return -1;
			}
		}

		if(config.isUnderwater() && y + rty + ty >= placer.getFluidHeight())
		{
			return -1;
		}

		if(!config.getClamp().canPlace(y + rty + ty, y - rty + ty))
		{
			return -1;
		}

		if(config.isBore())
		{
			for(int i = x - Math.floorDiv(w, 2); i <= x + Math.floorDiv(w, 2) - (w % 2 == 0 ? 1 : 0); i++)
			{
				for(int j = y - Math.floorDiv(h, 2); j <= y + Math.floorDiv(h, 2) - (h % 2 == 0 ? 1 : 0); j++)
				{
					for(int k = z - Math.floorDiv(d, 2); k <= z + Math.floorDiv(d, 2) - (d % 2 == 0 ? 1 : 0); k++)
					{
						placer.set(i, j, k, AIR);
					}
				}
			}
		}

		for(BlockVector g : blocks.keySet())
		{
			BlockVector i = g.clone();
			i = config.getRotation().rotate(i.clone(), spinx, spiny, spinz).clone();
			i = config.getTranslate().translate(i.clone(), config.getRotation(), spinx, spiny, spinz).clone();
			BlockData data = blocks.get(g).clone();

			if(placer.isPreventingDecay() && data instanceof Leaves && !((Leaves) data).isPersistent())
			{
				((Leaves) data).setPersistent(true);
			}

			for(IrisObjectReplace j : config.getEdit())
			{
				for(BlockData k : j.getFind())
				{
					if(j.isExact() ? k.matches(data) : k.getMaterial().equals(data.getMaterial()))
					{
						data = j.getReplace(rng, i.getX() + x, i.getY() + y, i.getZ() + z).clone();
					}
				}
			}

			data = config.getRotation().rotate(data, spinx, spiny, spinz);
			int xx = x + (int) Math.round(i.getX());
			int yy = y + (int) Math.round(i.getY());
			int zz = z + (int) Math.round(i.getZ());

			if(yv < 0 && config.getMode().equals(ObjectPlaceMode.PAINT))
			{
				yy = (int) Math.round(i.getY()) + Math.floorDiv(h, 2) + placer.getHighest(xx, zz, config.isUnderwater());
			}

			if(heightmap != null)
			{
				ChunkPosition pos = new ChunkPosition(xx, zz);

				if(!heightmap.containsKey(pos))
				{
					heightmap.put(pos, yy);
				}

				if(heightmap.get(pos) < yy)
				{
					heightmap.put(pos, yy);
				}
			}

			if(config.isMeld() && !placer.isSolid(xx, yy, zz))
			{
				continue;
			}

			if(config.isWaterloggable() && yy <= placer.getFluidHeight() && data instanceof Waterlogged)
			{
				((Waterlogged) data).setWaterlogged(true);
			}

			if(listener != null)
			{
				listener.accept(new BlockPosition(xx, yy, zz));
			}

			placer.set(xx, yy, zz, data);
		}

		if(heightmap != null)
		{
			RNG rngx = rng.nextParallelRNG(3468854);

			for(ChunkPosition i : heightmap.k())
			{
				int vx = i.getX();
				int vy = heightmap.get(i);
				int vz = i.getZ();

				if(config.getSnow() > 0)
				{
					BlockData bd = placer.get(vx, vy, vz);
					if(bd != null && bd.getMaterial().equals(SNOW))
					{
						continue;
					}

					int height = rngx.i(0, (int) (config.getSnow() * 7));
					placer.set(vx, vy + 1, vz, SNOW_LAYERS[Math.max(Math.min(height, 7), 0)]);
				}
			}
		}

		return y;
	}

	public void place(Location at)
	{
		for(BlockVector i : blocks.keySet())
		{
			at.clone().add(0, getCenter().getY(), 0).add(i).getBlock().setBlockData(blocks.get(i), false);
		}
	}
}
